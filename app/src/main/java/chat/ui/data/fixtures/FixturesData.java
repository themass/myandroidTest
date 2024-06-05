package chat.ui.data.fixtures;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.UUID;

/*
 * Created by Anton Bevza on 1/13/17.
 */
public abstract class FixturesData {

    static SecureRandom rnd = new SecureRandom();

    ;

    public static String getRandomId() {
        return Long.toString(UUID.randomUUID().getLeastSignificantBits());
    }

}
