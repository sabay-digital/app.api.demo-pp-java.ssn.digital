package digital.ssn.demopp;

import spark.Route;
import spark.Request;
import spark.Response;
import java.io.IOException;
import java.nio.charset.*;
import java.util.HashMap;
import com.google.common.hash.*;
import com.google.common.io.BaseEncoding;
import com.google.gson.*;
import digital.ssn.java.sdk.*;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Network;

public class PaymentsController {

    public static Route onetimeCharge = (Request request, Response response) -> {
        //
        // Request
        //
        
        // Parse request body to get URL encoded vals
        String[] bodyKeyVals = request.body().split("&");
        HashMap<String, String> reqBody = new HashMap<String, String>(); 
        for (int i = 0; i < bodyKeyVals.length; i++) {
            String[] keyVal = bodyKeyVals[i].split("=");
            reqBody.put(keyVal[0], keyVal[1]);
        }

        //
        // Logic
        //

        // Response
        PPResponse resp = new PPResponse();

        // SSN API connection
        String ssnAPI = "https://api.testing.ssn.digital/v1";
        // SSN Network object
        Network ssnNetwork = new Network("ssn_testing_network");
        // Load SSN asset issuing keypair
        KeyPair kp = KeyPair.fromSecretSeed("SCYFJ2F2SXBZNBFFLHQ42DB3PO3IWXHUO4FTMGR7SBTUOEX3MANUDK3K"); // GARXYJB3ZUJ7DNQTDWYE5PWO356ZOXD26NQJZPPM7CDAQP5YPWAWHD36
        // Load Signing keypair
        KeyPair signer = KeyPair.fromSecretSeed("SAR47TEN7SO2M3EYHSAKK6BSQQL6GB6XB5KYWND5B5MHXROISXNU4XUK"); // GCPVBRA53BRANESX7BXA5MVOVTB2SSQEYFP6Z2NACIZHBXALHQDWO3KW
        
        // Step 1 - Resolve payment address: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-1---resolve-the-payment-address
        String paURL = "https://pa.ssn.digital/v2/resolve/"+request.params(":pa");

        // Hash
        HashCode h = Hashing.sha256().hashString(paURL, StandardCharsets.UTF_8);

        // Sign
        byte[] s = kp.sign(h.asBytes());

        // Encode results in hex
        String paHash = h.toString();
        String paSig = BaseEncoding.base16().encode(s);

        ResolvedPaymentAddress payment = ResolvedPaymentAddress.ResolvePA(request.params(":pa"), paHash, paSig, kp.getAccountId(), "https://pa.ssn.digital/v2");

        // Step 2 - Verify Request Signature: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-2---verify-the-requst-signature-is-valid
        boolean sigVerified = Signature.Verify(reqBody.get("hash"), reqBody.get("signature"), reqBody.get("public_key"), ssnAPI);
        boolean signerVerified = Signature.VerifySigner(reqBody.get("public_key"), payment.network_address, ssnAPI);

        if (sigVerified && signerVerified) {
            // Step 3 - Verify Trust: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-3---optionally-verify-the-trustline
            HashMap<String, String> paymentCCY = payment.getPaymentArray();
            paymentCCY.forEach((key, val) -> {
                try {
                    if (!Trust.Verify(payment.network_address, key, kp.getAccountId(), ssnAPI)) {
                        paymentCCY.remove(key);
                    }
                } catch (IOException e) {
                    System.out.println("Something went wrong");
                }
            });
            System.out.println(paymentCCY.toString());
            if (paymentCCY.size() > 0) {
                // Step 4 - Payment Provider authorization: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-4---user-authorizes-the-payment-payment-provider-moves-the-amount-to-escrow

                // At this point the payment provider's UI/UX should takeover to authorize the user and deduct the funds
                // UI/UX flow should take account of the resolved payment details as documented here: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-1---resolve-the-payment-address

                // Step 5 - Build and Sign SSN payment: https://github.com/sabay-digital/org.ssn.doc.public/blob/master/tg/tg001.md#step-5---build-and-sign-the-payment-for-ssn
                // Build Txn
                String xdr = Payment.Create(kp.getAccountId(), payment.network_address, paymentCCY.get("USD"), "USD", kp.getAccountId(), "test", ssnAPI);
                String signedXdr = Payment.Sign(xdr, signer, ssnNetwork);
                String result =  Payment.Submit(signedXdr, ssnAPI);
                resp.status = 200;
                resp.hash = result;
            }
        } else {
            resp.status = 400;
            resp.title = "Something went wrong";
        }
       
        // Gson parser
        Gson gson = new Gson();

        // Write out a response
        response.status(resp.status);
        response.type("application/json");
        return gson.toJson(resp);
    };
}

class PPResponse {
    public int status;
    public String title;
    public String hash;
}