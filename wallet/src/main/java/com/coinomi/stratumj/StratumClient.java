package com.coinomi.stratumj;

import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.MessageException;
import com.coinomi.stratumj.messages.ResultMessage;
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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


/**
 * @author Giannis Dzegoutanis
 */
public class StratumClient extends AbstractExecutionThreadService {
	private static final Logger log = LoggerFactory.getLogger(StratumClient.class);
	private final int NUM_OF_WORKERS = 2;

	private AtomicLong idCounter = new AtomicLong();
	private ImmutableList<ServerAddress> serverAddresses;
	private Socket socket;
	private DataOutputStream toServer;
	private BufferedReader fromServer;

	final private ExecutorService pool = Executors.newFixedThreadPool(NUM_OF_WORKERS);

	final private ConcurrentHashMap<Long, SettableFuture<ResultMessage>> callers =
			new ConcurrentHashMap<Long, SettableFuture<ResultMessage>>();

	final private ConcurrentHashMap<Long, SubscribeResult> subscribes =
			new ConcurrentHashMap<Long, SubscribeResult>();

	final private BlockingQueue<ResultMessage> queue = new LinkedBlockingDeque<ResultMessage>();


	public interface SubscribeResult {
		public void handle(ResultMessage message);
	}

	private class MessageHandler implements Runnable {
		@Override
		public void run() {
			while (!pool.isShutdown()) {
				ResultMessage message = null;
				try {
					message = queue.take();
				} catch (InterruptedException ignored) {
				}

				if (message != null) {
					handle(message);
				}
			}
			log.debug("Shut down message handler thread: " + Thread.currentThread().getName());
		}

		private void handle(ResultMessage reply) {
			if (callers.containsKey(reply.getId())) {
				SettableFuture<ResultMessage> future = callers.get(reply.getId());
				future.set(reply);
				callers.remove(reply.getId());
			} else if (subscribes.containsKey(reply.getId())) {
				SubscribeResult handler = subscribes.get(reply.getId());
				try {
					log.debug("Running subscriber handler with result: " + reply);
					handler.handle(reply);
				} catch (RuntimeException e) {
					log.error("Error while executing subscriber handler", e);
				}
			} else {
				log.error("Got reply from server by could find any caller or subscriber",
						new MessageException("Orphaned reply", reply.toString()));
			}
		}
	}


	public StratumClient(List<ServerAddress> address) {
		serverAddresses = ImmutableList.copyOf(address);
	}

	public StratumClient(ServerAddress address) {
		serverAddresses = ImmutableList.of(address);
	}

	public StratumClient(String host, int port) {
		serverAddresses = ImmutableList.of(new ServerAddress(host, port));
	}

	protected Socket createSocket() throws IOException {
		// TODO use random, exponentially backoff from failed connections
		ServerAddress address = serverAddresses.get(0);
		log.debug("Opening a socket to " + address.getHost() + ":" + address.getPort());
		return new Socket(address.getHost(), address.getPort());
	}

	@Override
	protected void startUp() throws Exception {
		for (int i = 0; i < NUM_OF_WORKERS; i++) {
			pool.submit(new MessageHandler());
		}
		socket = createSocket();
		log.debug("Creating I/O streams to socket: " + socket);
		toServer = new DataOutputStream(socket.getOutputStream());
		fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	@Override
	protected void triggerShutdown() {
		try {
			socket.close();
		} catch (IOException e) {
			log.error("Failed to close socket", e);
		}
		pool.shutdown();
	}

	@Override
	protected void run() {
		log.debug("Start listening to server replies");

		String serverMessage;
		while (true) {
			try {
				serverMessage = fromServer.readLine();
			} catch (IOException e) {
				log.error("Error communicating with server", e);
				triggerShutdown();
				break;
			}

			if (serverMessage == null) {
				log.debug("Server closed communications. Shutting down");
				triggerShutdown();
				break;
			}

			log.debug("Got message from server: " + serverMessage);

			ResultMessage reply;
			try {
				reply = new ResultMessage(serverMessage);
			} catch (JSONException e) {
				log.error("Server sent malformed data", e);
				continue;
			}

			if (reply.errorOccured()) {
				Exception e = new MessageException(reply.getError(), reply.getFailedRequest());
				log.error("Failed call", e);
				// TODO set exception to the correct future object
//				if (callers.containsKey()) {
//					SettableFuture<ResultMessage> future = callers.get();
//					future.setException(e);
//				}
//				else {
//					log.error("Failed orphaned call", e);
//				}
			} else {
				boolean added = false;
				while (!added) {
					try {
						queue.put(reply);
						added = true;
					} catch (InterruptedException e) {
						log.debug("Interrupted while putting server reply to queue. Retrying...");
					}
				}

			}
		}
		log.debug("Finished listening for server replies");
	}

	public ListenableFuture<ResultMessage> call(CallMessage message) throws IOException {
		message.setId(idCounter.getAndIncrement());
		toServer.writeBytes(message.toString());

		SettableFuture<ResultMessage> future = SettableFuture.create();

		callers.put(message.getId(), future);

		return future;
	}

	public void subscribe(CallMessage message, SubscribeResult handler) throws IOException {
		message.setId(idCounter.getAndIncrement());
		toServer.writeBytes(message.toString());

		subscribes.put(message.getId(), handler);
	}
}
