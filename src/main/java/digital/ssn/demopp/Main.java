package digital.ssn.demopp;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 *
 * API Routes
 * 
 * To build: mvn clean compile assembly:single
 *
 */
public class Main 
{
    public static void main( String[] args )
    {
        post("/charge/onetime/:pa", PaymentsController.onetimeCharge);
    }
}
