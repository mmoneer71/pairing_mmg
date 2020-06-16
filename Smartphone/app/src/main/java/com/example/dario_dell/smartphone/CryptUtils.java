package com.example.dario_dell.smartphone;


import android.util.Log;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class CryptUtils {


    private final static String TAG = "CryptUtils";
    // ECDH Key size in bits
    private static final int EC_KEY_SIZE = 521;
    // Nonce size in bits
    private static final int NONCE_SIZE = 1024;
    // Hash function output size
    static final int HASH_SIZE = 512;
    // Unique ID byte size
    private static final int ID_SIZE = 288;
    // EC private key component
    private PrivateKey ecPriv;
    // Current session Key
    private SecretKeySpec sessionKey;
    // Current commitment opening
    private byte[] commitmentOpening;
    // Latest decrypted commitment received from other device
    private byte[] decryptedCommitment;


    byte[] genECDHKeyPair() {
        try {
            // Generate a DH key pair
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(EC_KEY_SIZE);
            KeyPair keyPair = gen.generateKeyPair();
            this.ecPriv = keyPair.getPrivate();
            return keyPair.getPublic().getEncoded();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error occurred when creating the Elliptic-curve params", e);
            return null;
        }

    }

    private byte[] genNonce() {
        BigInteger nonce;
        do {
            nonce = new BigInteger(NONCE_SIZE, (new SecureRandom()));
        } while (nonce.toByteArray().length != NONCE_SIZE / 8);
        return nonce.toByteArray();
    }


    void computeECDHSessionKey(byte[] pubComponent) {

        if (this.sessionKey != null) {
            return;
        }

        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(pubComponent);
            PublicKey otherPublicKey = kf.generatePublic(pkSpec);

            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(this.ecPriv);
            ka.doPhase(otherPublicKey, true);

            // Compress the key to 256 bits by deriving it with a hash function
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] dhKeyComp = digest.digest(ka.generateSecret());

            // Return an AES-256 key
            sessionKey = new SecretKeySpec(dhKeyComp, "AES");

        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException e) {
            Log.e(TAG, "Error occurred when generating the session key", e);
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

    byte[] genCommitment(String id,
                         List<Float>noisyInputX,
                         List<Float>noisyInputY) {


        byte[] idBytes = id.getBytes();
        byte[] nonceBytes = genNonce();
        byte[] sessionKeyBytes = sessionKey.getEncoded();
        byte[] noisyInputXBytes = floatListToByteArray(noisyInputX);
        byte[] noisyInputYBytes = floatListToByteArray(noisyInputY);


        this.commitmentOpening = mergeArrays(idBytes, noisyInputXBytes, noisyInputYBytes, nonceBytes);
        byte[] commitmentMsg = mergeArrays(this.commitmentOpening, sessionKeyBytes);
        return SHA512(commitmentMsg);
    }

    byte[] openCommitment() {
        return crypt(this.commitmentOpening, sessionKey, Cipher.ENCRYPT_MODE);
    }

    boolean verifyCommitment(byte[] commitmentOpening, String commitmentHash, String uniqueId) {
        this.decryptedCommitment = crypt(commitmentOpening, sessionKey, Cipher.DECRYPT_MODE);
        if (this.decryptedCommitment == null) {
            return false;
        }

        String decryptedUniqueId = new String(Arrays.copyOfRange(this.decryptedCommitment, 0, ID_SIZE / 8));
        if (uniqueId.equals(decryptedUniqueId)) {
            return false;
        }
        byte[] commitmentToVerify = mergeArrays(this.decryptedCommitment, sessionKey.getEncoded());

        return Arrays.equals(SHA512(commitmentToVerify), Base64.getDecoder().decode(commitmentHash));
    }

    void getDecryptedNoisyInput(List<Float> noisyInputX, List<Float> noisyInputY) {

        int noisyInputSize = (decryptedCommitment.length - ID_SIZE / 8 - NONCE_SIZE / 8) / 2;

        byte[] noisyInputXBytes = Arrays.copyOfRange(this.decryptedCommitment, ID_SIZE / 8,
                ID_SIZE / 8 + noisyInputSize);
        byte[] noisyInputYBytes = Arrays.copyOfRange(this.decryptedCommitment, ID_SIZE / 8 + noisyInputSize,
                ID_SIZE / 8 + 2 * noisyInputSize);

        for (int i = 0; i < noisyInputSize; i += Float.SIZE / 8) {
            ByteBuffer inputXBuffer = ByteBuffer.wrap(Arrays.copyOfRange(noisyInputXBytes, i, i + Float.SIZE / 8));
            ByteBuffer inputYBuffer = ByteBuffer.wrap(Arrays.copyOfRange(noisyInputYBytes, i, i + Float.SIZE / 8));

            noisyInputX.add(inputXBuffer.getFloat());
            noisyInputY.add(inputYBuffer.getFloat());
        }
    }

    private byte[] crypt(byte[] msg, SecretKeySpec key, int mode) {
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
                    return Base64.getEncoder().encode(msgEncrypted);
                case Cipher.DECRYPT_MODE:
                    return cipher.doFinal(Base64.getDecoder().decode(msg));
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Error occurred during encryption/decryption", e);
            return null;
        }
        return null;
    }

    byte[] mergeArrays(byte[]... arrays)
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

    private byte[] floatListToByteArray(List<Float> input) {

        List<Byte>resBytes = new ArrayList<>();
        for (Float val: input) {
            byte[] valBytes = ByteBuffer.allocate(Float.SIZE / 8).putFloat(val).array();

            for (byte byteVal : valBytes) {
                resBytes.add(byteVal);
            }
        }

        return convertBytes(resBytes);
    }

    private byte[] convertBytes(List<Byte> bytes)
    {
        byte[] ret = new byte[bytes.size()];
        Iterator<Byte> iterator = bytes.iterator();
        for (int i = 0; i < ret.length; i++)
        {
            ret[i] = iterator.next();
        }
        return ret;
    }
}