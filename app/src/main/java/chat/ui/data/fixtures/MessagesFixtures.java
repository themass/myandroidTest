package chat.ui.data.fixtures;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import chat.ui.data.model.Message;
import chat.ui.data.model.User;

/*
 * Created by troy379 on 12.12.16.
 */
public final class MessagesFixtures{
    private MessagesFixtures() {
        throw new AssertionError();
    }
    public static String getRandomId() {
        return Long.toString(UUID.randomUUID().getLeastSignificantBits());
    }
}
