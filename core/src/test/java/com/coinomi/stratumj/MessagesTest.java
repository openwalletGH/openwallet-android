package com.coinomi.stratumj;

import com.coinomi.stratumj.messages.BaseMessage;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class MessagesTest {

    @Test
    public void testBaseMessage() {
        BaseMessage base = new BaseMessage(999);
        Assert.assertEquals(base.getId(), 999);
        Assert.assertEquals(base.getError(), "");
        Assert.assertEquals(base.getFailedRequest(), "");
    }

    @Test
    public void testBaseMessage2() {
        BaseMessage base = new BaseMessage();
        Assert.assertEquals(base.getId(), -1);
        Assert.assertEquals(base.toString(), "{\"id\":null}\n");
    }

    @Test
    public void testBaseMessageParse() throws JSONException {
        String message = "{\"id\": 999, \"method\": \"blockchain.headers.subscribe\", \"params\": []}";
        BaseMessage base = BaseMessage.fromJson(message);

        Assert.assertEquals(base.getId(), 999);
    }

    @Test
    public void testBaseMessageParseFailed() throws JSONException {
        String message = "{\"request\": \"Lorem ipsum dolor sit amet, consectetuer adipiscing elit.\", " +
                "\"error\": \"bad JSON\"}";
        BaseMessage base = BaseMessage.fromJson(message);

        Assert.assertTrue(base.errorOccured());
        Assert.assertEquals(base.getError(), "bad JSON");
        Assert.assertEquals(base.getFailedRequest(), "Lorem ipsum dolor sit amet, consectetuer adipiscing elit.");
    }

    @Test
    public void testCallMessageParse() throws JSONException {
        String message = "{\"id\": 0, \"method\": \"blockchain.headers.subscribe\", \"params\": []}";
        CallMessage call = CallMessage.fromJson(message);

        Assert.assertEquals(call.getMethod(), "blockchain.headers.subscribe");
    }

    @Test
    public void testCallMessageSubscribe() {
        CallMessage call = new CallMessage("blockchain.headers.subscribe", new ArrayList());

        Assert.assertEquals(call.getMethod(), "blockchain.headers.subscribe");
        Assert.assertEquals("{\"id\":null,\"method\":\"blockchain.headers.subscribe\",\"params\":[]}\n",
                call.toString());
    }


    @Test
    public void testCallMessage() {
        CallMessage call = new CallMessage(1L, "blockchain.address.listunspent",
                Arrays.asList("npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf"));

        Assert.assertEquals(call.getId(), 1L);
        Assert.assertEquals(call.getMethod(), "blockchain.address.listunspent");
        Assert.assertEquals("{\"id\":1,\"method\":\"blockchain.address.listunspent\"," +
                "\"params\":[\"npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf\"]}\n", call.toString());
    }

    @Test
    public void testCallMessage2() {
        CallMessage call = new CallMessage(1L, "blockchain.address.listunspent", "npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf");

        Assert.assertEquals(call.getMethod(), "blockchain.address.listunspent");
        Assert.assertEquals("{\"id\":1,\"method\":\"blockchain.address.listunspent\"," +
                "\"params\":[\"npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf\"]}\n", call.toString());
    }


    @Test
    public void testCallMessage3() {
        CallMessage call = new CallMessage("blockchain.address.listunspent", (List) null);
        call.setParam("npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf");

        Assert.assertEquals("{\"id\":null,\"method\":\"blockchain.address.listunspent\"," +
                "\"params\":[\"npF3ApeWwMS8kwXJyybPZ76vNbv5txVjDf\"]}\n", call.toString());
    }

    @Test
    public void testResultMessage() throws JSONException {
        String resultString = "{\"id\": 1, \"result\": [{" +
                "\"tx_hash\": \"3aa2a5a9825ca767e092bcc19487aa13969eeb217fd0fba8492543bbb8c30954\", " +
                "\"height\": 260144}]}";
        ResultMessage result = ResultMessage.fromJson(resultString);

        Assert.assertEquals(result.getId(), 1L);
        Assert.assertTrue(result.getResult().length() > 0);
        Assert.assertEquals(result.getResult().getJSONObject(0).getString("tx_hash"),
                "3aa2a5a9825ca767e092bcc19487aa13969eeb217fd0fba8492543bbb8c30954");
        Assert.assertEquals(result.getResult().getJSONObject(0).getInt("height"), 260144);
    }
}