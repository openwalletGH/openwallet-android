package com.coinomi.stratumj;


import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;
import org.bitcoinj.utils.BriefLogFormatter;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * @author John L. Jegutanis
 */
public class CommunicationsTest {

    private Socket socket;
    private StratumClient client;
    private ByteArrayOutputStream serverInput;
    private PipedOutputStream serverOutput;

    public CommunicationsTest() {
//        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
        BriefLogFormatter.init();
    }

    @Before
    public void setup() throws IOException {
        socket = mock(Socket.class);

        serverInput = new ByteArrayOutputStream();
        serverOutput = new PipedOutputStream();
        PipedInputStream pipedServerOutput = new PipedInputStream(serverOutput);
        when(socket.getOutputStream()).thenReturn(serverInput);
        when(socket.getInputStream()).thenReturn(pipedServerOutput);
        when(socket.isConnected()).thenReturn(true);


        client = new StratumClient("not used", 1234) {
            @Override
            protected Socket createSocket() {
                return CommunicationsTest.this.socket;
            }
        };
    }

    @Test
    public void testCallCommand() throws Exception {

        client.startAsync();
        client.awaitRunning(5, TimeUnit.SECONDS);


        CallMessage call = new CallMessage("blockchain.address.get_history",
                Arrays.asList("mrx4EmF6zHXky3zDoeJ1K7KvYcuNn8Mmc4"));
        final ListenableFuture<ResultMessage> futureReply = client.call(call);

        // Check if server got the correct message
        Assert.assertEquals(call.toString(), new String(serverInput.toByteArray()));

        // Reply to the client
        String resultJson = "{\"id\": 0, \"result\": [{" +
                "\"tx_hash\": \"3aa2a5a9825ca767e092bcc19487aa13969eeb217fd0fba8492543bbb8c30954\", " +
                "\"height\": 260144}]}";
        final ResultMessage serverResult = ResultMessage.fromJson(resultJson);
        serverResult.setId(call.getId());
        serverOutput.write(serverResult.toString().getBytes());


        ResultMessage result = futureReply.get(3, TimeUnit.SECONDS);

        Assert.assertEquals(serverResult.toString(), result.toString());

        serverOutput.close();
    }

    @Test
    public void testIoFail() throws TimeoutException, IOException, JSONException {
        final AtomicBoolean success = new AtomicBoolean(false);
        final Thread testThread = Thread.currentThread();

        serverInput = mock(ByteArrayOutputStream.class);
        when(socket.getOutputStream()).thenReturn(serverInput);
        doThrow(IOException.class).when(serverInput).write(anyInt());

        client.startAsync();
        client.awaitRunning(5, TimeUnit.SECONDS);

        CallMessage call = CallMessage.fromJson("{}");
        final ListenableFuture<ResultMessage> futureReply = client.call(call);

        Futures.addCallback(futureReply, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(@Nullable ResultMessage result) {
                Assert.fail();
            }

            @Override
            public void onFailure(Throwable t) {
                Assert.assertTrue(t instanceof IOException);
                success.set(true);
                testThread.interrupt();
            }
        });

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) { }

        Assert.assertTrue(success.get());
    }

    @Test
    public void testSubscribeCommand() throws IOException, TimeoutException, ExecutionException, InterruptedException, JSONException {

        client.startAsync();
        client.awaitRunning(5, TimeUnit.SECONDS);

        final AtomicBoolean success = new AtomicBoolean(false);
        final Thread testThread = Thread.currentThread();

        // Call message
        CallMessage call = new CallMessage("blockchain.address.subscribe",
                Arrays.asList("mrx4EmF6zHXky3zDoeJ1K7KvYcuNn8Mmc4"));

        String subscribeCallJson = "{\"params\": [\"mrx4EmF6zHXky3zDoeJ1K7KvYcuNn8Mmc4\"," +
                "\"29b083b2b25cfbc8cc4b46977b74512a8cc7bc152f4533c1b2e50f1489d6df67\"]," +
                "\"id\": null, \"method\": \"blockchain.address.subscribe\"}";
        final CallMessage subscribeCall = CallMessage.fromJson(subscribeCallJson);

        // Subscribe
        ListenableFuture<ResultMessage> futureReply = client.subscribe(call, new StratumClient.SubscribeResultHandler() {
            @Override
            public void handle(CallMessage message) {
                Assert.assertEquals(subscribeCall.toString(), message.toString());
                success.set(true);
                testThread.interrupt();
            }
        });

        // Check if server got the correct message
        Assert.assertEquals(call.toString(), new String(serverInput.toByteArray()));

        // Result message
        String resultJson = "{\"id\": 0, \"result\":" +
                "\"e0dc94c40d4331306eb60ad55b537ee92446b8a8dc19f25dc3373d96c1904719\"}";
        final ResultMessage serverResult = ResultMessage.fromJson(resultJson);
        serverResult.setId(call.getId());

        // Reply to the client
        serverOutput.write(serverResult.toString().getBytes());

        ResultMessage result = futureReply.get(3, TimeUnit.SECONDS);

        Assert.assertEquals(serverResult.toString(), result.toString());

        // Send a message from server to the client
        serverOutput.write(subscribeCall.toString().getBytes());

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        serverOutput.close();

        Assert.assertTrue(success.get());
    }
}
