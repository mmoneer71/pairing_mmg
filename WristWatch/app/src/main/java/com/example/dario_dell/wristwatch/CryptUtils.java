package com.example.dario_dell.wristwatch;


import android.util.Log;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptUtils {


    private final static String TAG = "CryptUtils";
    // Key size
    static final int KEY_SIZE = 1024;
    // Hash function output size
    static final int HASH_SIZE = 512;
    // Generator: Has to be primitive root
    private BigInteger g;
    // Prime value
    private BigInteger p;
    // Public key component
    private BigInteger pub;
    // Private key component
    private BigInteger priv;
    // Current session Key
    private SecretKeySpec sessionKey;

    // Parameters extraction based on:
    // https://stackoverflow.com/questions/19323178/how-to-do-diffie-hellman-key-generation-and-retrieve-raw-key-bytes-in-java
    void initDHParams() {
        try {
            // Generate a DH key pair
            KeyPairGenerator gen = KeyPairGenerator.getInstance("DH");
            gen.initialize(KEY_SIZE);
            KeyPair keyPair = gen.genKeyPair();

            // Separate the private and the public key from the key pair
            priv = ((javax.crypto.interfaces.DHPrivateKey) keyPair.getPrivate()).getX();
            pub = ((javax.crypto.interfaces.DHPublicKey) keyPair.getPublic()).getY();

            // Extract the safe prime and the generator number from the key pair
            DHParameterSpec params = ((javax.crypto.interfaces.DHPublicKey) keyPair.getPublic()).getParams();
            p = params.getP();
            g = params.getG();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error occurred when creating the Diffie-Hellman params", e);
        }

    }

    void setDHParams() {
        p = new BigInteger("158226922697030617948797227800498624915189325504023952753861279447483239021860246817118586173001979901983796625583670102839781534905294538416934403704582647288855845359398886205261258488868149392541793104557804708023361921748188126306804286723526796069672484804981694321100425357802703970505567917675490154159");
        g = new BigInteger("88173088025497969607285612227517451555521798225119643685974814908304000890896133241125234093687057175629737474938680821119549216182920507795942634570963329026513429453884778473255437919969740174644539207004023810453975179883613841204932790018405615190065694910358431372613294863132989048355311612311291989235");

    }

    BigInteger genKeyPair() {
        do {
            this.priv = new BigInteger(KEY_SIZE, (new Random()));

            // Compute the public key component y
            this.pub = this.g.modPow(priv, this.p);
        } while (this.pub.toByteArray().length != KEY_SIZE / 8 || this.priv.toByteArray().length != KEY_SIZE / 8);

        return this.pub;
    }

    private BigInteger genNonce() {
        BigInteger nonce;
        do {
            nonce = new BigInteger(KEY_SIZE, (new Random()));
        } while (nonce.toByteArray().length != KEY_SIZE / 8);

        return nonce;
    }

    SecretKeySpec computeSessionKey(BigInteger pubComponent) {

        if (sessionKey != null) {
            return sessionKey;
        }

        try {
            // Compute the key: dhKey = (dh_pubComponent ^ dh_privKey) mod dh_P
            BigInteger dhKey = pubComponent.modPow(this.priv, this.p);

            // Compress the key to 256 bits by deriving it with a hash function
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] dhKeyComp = digest.digest(dhKey.toByteArray());

            // Return an AES-256 key
            sessionKey = new SecretKeySpec(dhKeyComp, "AES");

            return sessionKey;

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error occurred when generating the session key", e);
            return null;
        }

    }

    private byte[] SHA512(byte[] msg) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            return digest.digest(msg);

        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error occurred when generating the hashed message", e);
            return null;
        }
    }

    public byte[] genCommitment(String id,
                                List<Float>noisyInputX,
                                List<Float>noisyInputY) {

        int n = noisyInputX.size();

        byte[] idBytes = id.getBytes();
        byte[] nonceBytes = genNonce().toByteArray();
        byte[] sessionKeyBytes = sessionKey.getEncoded();
        byte[] noisyInputXBytes = new byte[n];
        byte[] noisyInputYBytes = new byte[n];

        for (int i = 0; i < n; ++i) {
            noisyInputXBytes[i] = noisyInputX.get(i).byteValue();
            noisyInputYBytes[i] = noisyInputY.get(i).byteValue();
        }

        byte[] commitmentMsg = merge(idBytes, noisyInputXBytes, noisyInputYBytes, nonceBytes, sessionKeyBytes);
        return SHA512(commitmentMsg);
    }

    public String crypt(byte[] msg, SecretKeySpec key, int mode) {
        try {
            // Set the cipher with the crypto primitive (AES), mode of operation (CBC) and padding scheme (PKCS5PADDING)
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");

            // Set the initialization vector for CBC
            byte[] ivBytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            // Initialize the cipher with the symmetric key and initialization vector
            cipher.init(mode, key, iv);

            // Do the encryption and return the ciphertext encoded into a Base64 string
            // Or do the decryption and return the plaintext
            switch (mode) {
                case Cipher.ENCRYPT_MODE:
                    byte[] msgEncrypted = cipher.doFinal(msg);
                    return Base64.getEncoder().encodeToString(msgEncrypted);
                case Cipher.DECRYPT_MODE:
                    byte[] msgDecrypted = cipher.doFinal(Base64.getDecoder().decode(msg));
                    return new String(msgDecrypted);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error occurred during encryption/decryption", e);
            return null;
        }
        return null;
    }

    private byte[] merge(byte[]... arrays)
    {
        int finalLength = 0;
        for (byte[] array : arrays) {
            finalLength += array.length;
        }

        byte[] dest = null;
        int destPos = 0;

        for (byte[] array : arrays)
        {
            if (dest == null) {
                dest = Arrays.copyOf(array, finalLength);
                destPos = array.length;
            } else {
                System.arraycopy(array, 0, dest, destPos, array.length);
                destPos += array.length;
            }
        }
        return dest;
    }
}
