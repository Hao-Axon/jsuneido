package suneido;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.io.*;

import org.ronsoft.nioserver.BufferFactory;
import org.ronsoft.nioserver.ChannelFacade;
import org.ronsoft.nioserver.InputHandler;
import org.ronsoft.nioserver.InputQueue;
import org.ronsoft.nioserver.impl.DumbBufferFactory;
import org.ronsoft.nioserver.impl.GenericInputHandlerFactory;
import org.ronsoft.nioserver.impl.NioDispatcher;
import org.ronsoft.nioserver.impl.StandardAcceptor;

/**
 * Using org.ronsoft.nioserver -
 * see <a href="http://javanio.info/filearea/nioserver/NIOServerMark2.pdf">
 *		How to Build a Scalable Multiplexed Server with NIO Mark II</a>
 * @author Andrew McKinlay
 */
public class Server {
	static final int PORT = 3456;
	
	public static void main(String[] args) throws IOException {
		start();
	}
		
	public static void start() throws IOException {
		Executor executor = Executors.newCachedThreadPool();
		BufferFactory bufFactory = new DumbBufferFactory (1024);
		NioDispatcher dispatcher = new NioDispatcher (executor, bufFactory);
		StandardAcceptor acceptor = new StandardAcceptor (PORT, dispatcher,
			new GenericInputHandlerFactory(Handler.class));

		dispatcher.start();
		acceptor.newThread();
	}
	
	public static class Handler implements InputHandler {
		public ByteBuffer nextMessage(ChannelFacade channelFacade) {
			InputQueue inputQueue = channelFacade.inputQueue();
			int nlPos = inputQueue.indexOf((byte) '\n');
			if (nlPos == -1) 
				return null;
			return (inputQueue.dequeueBytes (nlPos + 1));
			//TODO handle requests that send additional data
		}

		public void handleInput(ByteBuffer message, ChannelFacade channelFacade) {
			//TODO just echo for now
			channelFacade.outputQueue().enqueue(message);
		}

		public void started(ChannelFacade channelFacade) {
			channelFacade.outputQueue().enqueue(
					ByteBuffer.wrap("jSuneido Server\n".getBytes()));
		}

		public void starting(ChannelFacade channelFacade) {
			// not needed
		}

		public void stopped(ChannelFacade channelFacade) {
			// not needed
		}

		public void stopping(ChannelFacade channelFacade) {
			// not needed
		}
	}
}
