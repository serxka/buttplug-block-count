package net.knao.s.buttplugblockcount;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ButtplugThread extends Thread {
	public static final Logger LOGGER = LoggerFactory.getLogger("buttplug-block-count");

	@Nullable
	private ButtplugClientWSClient client;
	@Nullable
	private ButtplugClientDevice device;
	@Nullable
	private List<ButtplugClientDevice> devices;

	private double scalar = 0.0;
	private double lastScalar = 0.0;

	@Nullable
	private URI reconnectAddress;
	private boolean tryReconnect = false;
	private boolean disconnect = false;
	@Nullable
	private FabricClientCommandSource cmdSource;

	ButtplugThread() {}

	public void run() {
		while (true) {
			runInner();

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// Ignore exception here
			}
		}
	}

	private void runInner() {
		// look, idk
		if (disconnect) {
			disconnect = false;
			if (client != null) {
				device = null;
				client.disconnect();
				client = null;
			}
		}

		if (tryReconnect) {
			try {
				client = new ButtplugClientWSClient("minecraft");
				if (reconnectAddress == null)
					reconnectAddress = new URI("ws://127.0.0.1:12345");
				client.connect(reconnectAddress);
				tryReconnect = false;
				client.startScanning();
				devices = client.getDevices();
				if (devices.size() != 0)
					device = devices.get(0);
			} catch (Exception e) {
				if (cmdSource != null)
					cmdSource.sendError(Text.literal("failed to connect to buttplug.io server, check log for more details")
						.styled(s -> s.withBold(true).withColor(Formatting.RED)));
				cmdSource = null;
				LOGGER.error("failed to connect to buttplug.io server");
				e.printStackTrace();
			}
		}
	}

	public synchronized void setFeedbackSource(FabricClientCommandSource source) {
		cmdSource = source;
	}

	public synchronized void tryConnect() {
		try {
			tryConnect(new URI("ws://127.0.0.1:12345"));
		} catch (URISyntaxException e) {
			// can't fail
		}
	}

	public synchronized void tryConnect(URI address) {
		tryReconnect = true;
		reconnectAddress = address;
	}

	public synchronized void disconnect() {
		disconnect = true;
	}

	public synchronized void setTargetVibrate(double value) {
		if (client == null || device == null || !client.isConnected())
			return;

		scalar = value;
		if (scalar != lastScalar) {
			try {
				device.sendScalarVibrateCmd(value);
			} catch (Exception e) {
				LOGGER.warn("failed to send vibrate command to buttplug " + device.getDisplayName());
			}
		}
		lastScalar = scalar;
	}
}
