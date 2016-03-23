package com.coinomi.stratumj;

import com.coinomi.stratumj.messages.BaseMessage;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.MessageException;
import com.coinomi.stratumj.messages.ResultMessage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author John L. Jegutanis
 */
public class StratumClient extends AbstractExecutionThreadService {
    private static final Logger log = LoggerFactory.getLogger(StratumClient.class);
    private final int NUM_OF_WORKERS = 1;

    private AtomicLong idCounter = new AtomicLong();
    private ServerAddress serverAddress;
    private Socket socket;
    @VisibleForTesting DataOutputStream toServer;
    BufferedReader fromServer;

    final private ExecutorService pool = Executors.newFixedThreadPool(NUM_OF_WORKERS);

    final private ConcurrentHashMap<Long, SettableFuture<ResultMessage>> callers =
            new ConcurrentHashMap<>();

    final private ConcurrentHashMap<String, List<SubscribeResultHandler>> subscribersHandlers =
            new ConcurrentHashMap<>();

    final private BlockingQueue<BaseMessage> queue = new LinkedBlockingDeque<BaseMessage>();


    public interface SubscribeResultHandler {
        void handle(CallMessage message);
    }

    private class MessageHandler implements Runnable {
        @Override
        public void run() {
            while (!pool.isShutdown()) {
                BaseMessage message = null;
                try {
                    message = queue.take();
                } catch (InterruptedException ignored) { }

                if (message != null) {
                    handle(message);
                }
            }
            log.info("Shutdown message handler thread: {}", Thread.currentThread().getName());
        }

        private void handle(BaseMessage message) {
            if (message instanceof ResultMessage) {
                ResultMessage reply = (ResultMessage) message;
                if (callers.containsKey(reply.getId())) {
                    SettableFuture<ResultMessage> future = callers.get(reply.getId());
                    future.set(reply);
                    callers.remove(reply.getId());
                } else {
                    log.error("Received reply from server, but could not find caller",
                            new MessageException("Orphaned reply", reply.toString()));
                }
            } else if (message instanceof CallMessage) {
                CallMessage reply = (CallMessage) message;
                if (subscribersHandlers.containsKey(reply.getMethod())) {
                    List<SubscribeResultHandler> subs;

                    synchronized (subscribersHandlers.get(reply.getMethod())) {
                        // Make a defensive copy
                        subs = ImmutableList.copyOf(subscribersHandlers.get(reply.getMethod()));
                    }

                    for (SubscribeResultHandler handler : subs) {
                        try {
                            log.debug("Running subscriber handler with result: " + reply);
                            handler.handle(reply);
                        } catch (Exception e) {
                            log.error("Error while executing subscriber handler", e);
                        }
                    }
                } else {
                    log.error("Received call from server, but not could find subscriber",
                            new MessageException("Orphaned call", reply.toString()));
                }

            } else {
                log.error("Unable to handle message",
                        new MessageException("Unhandled message", message.toString()));
            }
        }
    }

    public StratumClient(ServerAddress address) {
        serverAddress = address;
    }

    public StratumClient(String host, int port) {
        serverAddress = new ServerAddress(host, port);
    }

    public long getCurrentId() {
        return idCounter.get();
    }

    protected Socket createSocket() throws IOException {
        ServerAddress address = serverAddress;
        log.debug("Opening a socket to " + address.getHost() + ":" + address.getPort());

        return new Socket(address.getHost(), address.getPort());
    }

    @Override
    protected void startUp() {
        for (int i = 0; i < NUM_OF_WORKERS; i++) {
            pool.submit(new MessageHandler());
        }
        try {
            socket = createSocket();
            log.info("Creating I/O streams to socket: {}", socket);
            toServer = new DataOutputStream(socket.getOutputStream());
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (Exception e) {
            log.info("Unable to create socket for {}", serverAddress);
            triggerShutdown();
        }
    }

    @Override
    protected void triggerShutdown() {
        log.info("Shutting down {}", serverAddress);
        disconnect();
        pool.shutdownNow();
        for (SettableFuture<ResultMessage> future : callers.values()) {
            future.cancel(true);
        }
        callers.clear();
        subscribersHandlers.clear();
    }

    @Override
    protected void run() {
        log.debug("Started listening for server replies");

        String serverMessage;
        while (isRunning() && isConnected()) {
            try {
                serverMessage = fromServer.readLine();
            } catch (IOException e) {
                if (isRunning()) {
                    log.info("Error communicating with server: {}", e.getMessage());
                    triggerShutdown();
                }
                break;
            }

            if(serverMessage == null) {
                log.info("Server closed communications. Shutting down");
                triggerShutdown();
                break;
            }

            log.debug("Received message from server: " + serverMessage);

            BaseMessage reply;
            try {
                reply = BaseMessage.fromJson(serverMessage);
            } catch (JSONException e) {
                log.error("Server sent malformed data", e);
                continue;
            }

            if (reply.errorOccured()) {
                Exception e = new MessageException(reply.getError(), reply.getFailedRequest());
                log.error("Failed call", e);
                // TODO set exception to the correct future object
//                if (callers.containsKey()) {
//                    SettableFuture<ResultMessage> future = callers.get();
//                    future.setException(e);
//                } else {
//                    log.error("Failed orphaned call", e);
//                }
            } else {
                boolean added = false;

                try {
                    if (reply.isResult()) {
                        reply = ResultMessage.fromJson(serverMessage);
                    } else if (reply.isCall()) {
                        reply = CallMessage.fromJson(serverMessage);
                    }
                } catch (JSONException e) {
                    // Should not happen as we already checked this exception
                    throw new RuntimeException(e);
                }

                while (!added) {
                    try {
                        queue.put(reply);
                        added = true;
                    } catch (InterruptedException e) {
                        log.debug("Interrupted while adding server reply to queue. Retrying...");
                    }
                }

            }
        }
        log.info("Finished listening for server replies");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }


    public void disconnect() {
        if (isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.error("Unable to close socket", e);
            }
        }
    }

    public ListenableFuture<ResultMessage> call(CallMessage message) {
        SettableFuture<ResultMessage> future = SettableFuture.create();

        message.setId(idCounter.getAndIncrement());

        try {
            toServer.writeBytes(message.toString());
            callers.put(message.getId(), future);
        } catch (Throwable e) {
            future.setException(e);
            log.error("Error making a call to the server: {}", e.getMessage());
            triggerShutdown();
        }

        return future;
    }

    public ListenableFuture<ResultMessage> subscribe(CallMessage message, SubscribeResultHandler handler) {
        // Add the subscription handler the the subscribersHandlers map so that any future
        // subscription call will be handled by it.

        // If this particular method was not used before, initialize a list of handlers
        if (!subscribersHandlers.containsKey(message.getMethod())) {
            subscribersHandlers.put(message.getMethod(),
                    Collections.synchronizedList(new ArrayList<SubscribeResultHandler>()));
        }

        // Add handler if needed
        if (!subscribersHandlers.get(message.getMethod()).contains(handler)) {
            subscribersHandlers.get(message.getMethod()).add(handler);
        }

        // Make the subscription call, the server will reply immediately
        return call(message);
    }
}
