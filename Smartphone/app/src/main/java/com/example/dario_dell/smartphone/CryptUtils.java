package com.example.dario_dell.smartphone;

import android.util.Log;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptUtils {


    private final static String TAG = "CryptUtils";
    // Key size
    public static final int KEY_SIZE = 1024;
    // Generator: Has to be primitive root
    private BigInteger g;
    // Prime value
    private BigInteger p;
    // Public key component
    private BigInteger pub;
    // Private key component
    private BigInteger priv;

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

    SecretKeySpec computeSessionKey(BigInteger pubComponent) {
        try {
            // Compute the key: dhKey = (dh_pubComponent ^ dh_privKey) mod dh_P
            BigInteger dhKey = pubComponent.modPow(this.priv, this.p);

            // Compress the key to 256 bits by deriving it with a hash function
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] dhKeyComp = digest.digest(dhKey.toByteArray());

            // Return an AES-256 key
            return new SecretKeySpec(dhKeyComp, "AES");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Error occurred when generating the session key", e);
            return null;
        }

    }

}
