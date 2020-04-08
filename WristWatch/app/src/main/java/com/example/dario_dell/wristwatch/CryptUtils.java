package com.example.dario_dell.wristwatch;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import javax.crypto.spec.DHParameterSpec;

public class CryptUtils {

    // Generator: Has to be primitive root
    private BigInteger g;
    // Prime value
    private BigInteger p;
    // Private key component
    private BigInteger privKeyClient;
    // Public key component
    private BigInteger pubKeyClient;
    // Private key component
    private BigInteger privKeyServer;
    // Public key component
    private BigInteger pubKeyServer;

    // Parameters extraction based on:
    // https://stackoverflow.com/questions/19323178/how-to-do-diffie-hellman-key-generation-and-retrieve-raw-key-bytes-in-java
    void initDHParams() throws NoSuchAlgorithmException {
        // Generate a DH key pair
        KeyPairGenerator gen = KeyPairGenerator.getInstance("DH");
        gen.initialize(1024);
        KeyPair keyPair = gen.generateKeyPair();

        // Separate the private and the public key from the key pair
        privKeyClient = ((javax.crypto.interfaces.DHPrivateKey) keyPair.getPrivate()).getX();
        pubKeyClient = ((javax.crypto.interfaces.DHPublicKey) keyPair.getPublic()).getY();

        // Extract the safe prime and the generator number from the key pair
        DHParameterSpec params = ((javax.crypto.interfaces.DHPublicKey) keyPair.getPublic()).getParams();
        p = params.getP();
        g = params.getG();

    }

    public void genPubKey() {
        privKeyServer = new BigInteger(1024, (new Random()));

        // Compute the public key component dh_B
        pubKeyServer = g.modPow(privKeyServer, p);
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger getP() {
        return p;
    }

    public BigInteger getPrivKeyClient() {
        return privKeyClient;
    }

    public BigInteger getPubKeyClient() {
        return pubKeyClient;
    }

    public BigInteger getPrivKeyServer() {
        return privKeyServer;
    }

    public BigInteger getPubKeyServer() {
        return pubKeyServer;
    }

}
