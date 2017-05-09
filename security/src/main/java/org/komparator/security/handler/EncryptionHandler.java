package org.komparator.security.handler;

import static javax.xml.bind.DatatypeConverter.parseBase64Binary;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import com.sun.xml.messaging.saaj.util.ByteOutputStream;

import org.komparator.security.CryptoUtil;
import org.komparator.security.SecurityManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import pt.ulisboa.tecnico.sdis.cert.CertUtil;

/**
 * This SOAPHandler outputs the contents of inbound and outbound messages.
 */
public class EncryptionHandler implements SOAPHandler<SOAPMessageContext> {
	private static final String OPERATION_BUYCART = "buyCart";
	private static final String PARAM_CC_NUMBER = "creditCardNr";

	//
	// Handler interface implementation
	//

	/**
	 * Gets the header blocks that can be processed by this Handler instance. If
	 * null, processes all.
	 */
	@Override
	public Set<QName> getHeaders() {
		return null;
	}

	/**
	 * The handleMessage method is invoked for normal processing of inbound and
	 * outbound messages.
	 */
	@Override
	public boolean handleMessage(SOAPMessageContext smc) {
		Boolean outbound = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

		try {
			SOAPMessage msg = smc.getMessage();
			SOAPPart sp = msg.getSOAPPart();
			SOAPEnvelope se = sp.getEnvelope();
			SOAPBody body = se.getBody();

			QName operation = (QName) smc.get(MessageContext.WSDL_OPERATION);
			if (!operation.getLocalPart().equals(OPERATION_BUYCART)) {
				return true; // Not what we're looking for
			}

			NodeList nodes = body.getFirstChild().getChildNodes();
			for (int i = 0; i < nodes.getLength(); ++i) {
				Node argument = nodes.item(i);
				if (argument.getNodeName().equals(PARAM_CC_NUMBER)) {
					String creditCard = argument.getTextContent();
					byte[] result;

					if (outbound) {
						result = encrypt(parseBase64Binary(creditCard));
					} else {
						result = decrypt(parseBase64Binary(creditCard));
					}

					argument.setTextContent(printBase64Binary(result));
					msg.saveChanges();
					return true;
				}
			}

			// Safe to proceed
			return true;
		} catch (CertificateException ce) {
			String error = "Error retrieving certificate: " + ce.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		} catch (KeyStoreException kse) {
			String error = "Error loading keystore: " + kse.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		} catch (UnrecoverableKeyException uke) {
			String error = "Couldn't recover key: " + uke.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		} catch (SOAPException soape) {
			String error = "SOAP error: " + soape.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		} catch (FileNotFoundException fnfe) {
			String error = "File not found: " + fnfe.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		} catch (IOException ioe) {
			String error = "I/O error: " + ioe.getMessage();
			System.err.println(error);
			throw new RuntimeException(error);
		}
	}

	/** The handleFault method is invoked for fault message processing. */
	@Override
	public boolean handleFault(SOAPMessageContext smc) {
		return true;
	}

	/**
	 * Called at the conclusion of a message exchange pattern just prior to the
	 * JAX-WS runtime dispatching a message, fault or exception.
	 */
	@Override
	public void close(MessageContext messageContext) {
		// nothing to clean up
	}

	private byte[] encrypt(byte[] plainBytes)
			throws IOException, CertificateException {
		String sender = SecurityManager.getInstance().getDestination();
		Certificate certificate =
				CertUtil.getX509CertificateFromResource(sender + ".cer");
		PublicKey key = CertUtil.getPublicKeyFromCertificate(certificate);

		return CryptoUtil.asymCipher(key, plainBytes);
	}

	private byte[] decrypt(byte[] encryptedBytes)
			throws UnrecoverableKeyException, KeyStoreException,
			FileNotFoundException {
		String sender = SecurityManager.getInstance().getSender();
		String password = SecurityManager.getInstance().getPassword();
		KeyStore keyStore = CertUtil.readKeystoreFromResource(
				sender + ".jks", password.toCharArray());
		PrivateKey key = CertUtil.getPrivateKeyFromKeyStore(
				sender.toLowerCase(), password.toCharArray(), keyStore);

		return CryptoUtil.asymDecipher(key, encryptedBytes);
	}
}
