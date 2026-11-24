package com.toddlerlauncher.link

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shared by Toddler Launcher (the child's device) and Toddler Parent (the parent's phone).
 *
 * Pairing: both sides make a one-off key pair and swap the public halves
 * (elliptic-curve Diffie-Hellman, P-256). Each side can then work out the same
 * secret key, which never crosses the network. The parent PIN is sent once,
 * locked with that key, to prove it's really a parent. The key is kept by both
 * sides and every later message is locked with it (AES-256-GCM), so others on
 * the same Wi-Fi can't read, change or replay them.
 */
object Link {
    /** Name used to find child devices on the local network (mDNS / DNS-SD). */
    const val SERVICE_TYPE = "_toddlerlauncher._tcp."
    const val PORT = 8080

    /** How far apart the two devices' clocks may be, and how long a message counts as fresh. */
    const val MAX_AGE_MS = 10 * 60_000L

    private val random = SecureRandom()

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun randomId(bytes: Int = 12): String =
        ByteArray(bytes).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    fun publicKey(kp: KeyPair): String = b64(kp.public.encoded)

    /** The secret both sides arrive at, from our private key and their public key. */
    fun sharedKey(mine: PrivateKey, theirs: String, parentPub: String, childPub: String): ByteArray {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(theirs)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(mine)
        ka.doPhase(pub, true)
        val secret = ka.generateSecret()
        val md = MessageDigest.getInstance("SHA-256")
        md.update("toddler-launcher-pair-v1".toByteArray())
        md.update(secret)
        md.update(parentPub.toByteArray())
        md.update(childPub.toByteArray())
        return md.digest()
    }

    /** A short code made from the key, shown on both screens so you can see they match. */
    fun checkCode(key: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(key + "check".toByteArray())
        val n = ((d[0].toInt() and 0xff) shl 16) or ((d[1].toInt() and 0xff) shl 8) or (d[2].toInt() and 0xff)
        return "%06d".format(n % 1_000_000)
    }

    /** Locks text with the key: returns { iv, data }. */
    fun seal(key: ByteArray, text: String): JSONObject {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return JSONObject().put("iv", b64(iv)).put("data", b64(c.doFinal(text.toByteArray())))
    }

    /** Unlocks what seal() made. Throws if the key is wrong or anything was changed. */
    fun open(key: ByteArray, box: JSONObject): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(box.getString("iv"))))
        return String(c.doFinal(unb64(box.getString("data"))))
    }
}
