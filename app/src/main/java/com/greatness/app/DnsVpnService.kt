package com.greatness.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class DnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private val pool = Executors.newCachedThreadPool()
    private val fakeDns = "10.77.0.2"
    private val upstream = "94.140.14.15" // AdGuard Family DNS

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopFilter(); return START_NOT_STICKY }
        startFilter()
        return START_STICKY
    }

    private fun startFilter() {
        if (running) return
        Blocklist.load(this)
        tun = Builder()
            .setSession("Greatness")
            .addAddress("10.77.0.1", 32)
            .addDnsServer(fakeDns)
            .addRoute(fakeDns, 32)
            .establish() ?: return
        running = true
        Thread { loop() }.start()
    }

    private fun stopFilter() {
        running = false
        tun?.close(); tun = null
        stopSelf()
    }

    override fun onDestroy() { stopFilter(); super.onDestroy() }

    private fun loop() {
        val fd = tun ?: return
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { break }
            if (n <= 0) continue
            val pkt = buf.copyOf(n)
            pool.execute { handle(pkt, output) }
        }
    }

    private fun handle(p: ByteArray, out: FileOutputStream) {
        try {
            if (p.size < 28 || (p[0].toInt() shr 4) != 4 || p[9].toInt() != 17) return
            val ihl = (p[0].toInt() and 0x0F) * 4
            val dstPort = ((p[ihl + 2].toInt() and 0xFF) shl 8) or (p[ihl + 3].toInt() and 0xFF)
            if (dstPort != 53) return
            val srcPortHi = p[ihl]; val srcPortLo = p[ihl + 1]
            val dns = p.copyOfRange(ihl + 8, p.size)

            val (domain, qEnd) = parseQuestion(dns) ?: return
            val resp: ByteArray = if (Blocklist.isBlocked(domain)) {
                blockedReply(dns, qEnd)
            } else {
                forward(dns) ?: return
            }

            val total = 20 + 8 + resp.size
            val r = ByteArray(total)
            r[0] = 0x45; r[8] = 64; r[9] = 17
            r[2] = (total shr 8).toByte(); r[3] = total.toByte()
            System.arraycopy(p, 16, r, 12, 4)
            System.arraycopy(p, 12, r, 16, 4)
            val ck = checksum(r, 20)
            r[10] = (ck shr 8).toByte(); r[11] = ck.toByte()
            r[20] = 0; r[21] = 53
            r[22] = srcPortHi; r[23] = srcPortLo
            val udpLen = 8 + resp.size
            r[24] = (udpLen shr 8).toByte(); r[25] = udpLen.toByte()
            System.arraycopy(resp, 0, r, 28, resp.size)
            synchronized(out) { out.write(r) }
        } catch (_: Exception) { }
    }

    private fun parseQuestion(d: ByteArray): Pair<String, Int>? {
        if (d.size < 17) return null
        var i = 12
        val sb = StringBuilder()
        while (i < d.size) {
            val len = d[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len))
            i += len + 1
        }
        return Pair(sb.toString(), i + 4)
    }

    private fun blockedReply(q: ByteArray, qEnd: Int): ByteArray {
        val r = q.copyOf(minOf(qEnd, q.size))
        r[2] = 0x81.toByte()
        r[3] = 0x83.toByte()
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
        return r
    }

    private fun forward(q: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { s ->
                protect(s)
                s.soTimeout = 4000
                s.send(DatagramPacket(q, q.size, InetAddress.getByName(upstream), 53))
                val buf = ByteArray(4096)
                val dp = DatagramPacket(buf, buf.size)
                s.receive(dp)
                buf.copyOf(dp.length)
            }
        } catch (e: Exception) { null }
    }

    private fun checksum(b: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) {
            sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
