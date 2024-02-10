package net.knao.s.buttplugblockcount;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.text.Text;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.DoubleArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

public class ButtplugBlockCountClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("buttplug-block-count");

	private ButtplugThread buttplugThread = new ButtplugThread();
	private ArrayDeque<Long> blocksBroken = new ArrayDeque<Long>();
	private int cooldown = 6;
	private int blocks = 10;
	private double sensitivity = 0.8;
	private boolean enabled = true;

	@Override
	public void onInitializeClient() {
		// Start our buttplug communication in another thread
		buttplugThread.start();
		buttplugThread.tryConnect();

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerCommands(dispatcher);
		});

		ClientPlayerBlockBreakEvents.AFTER.register((world, eventPlayer, pos, state) -> {
			blocksBroken.addLast(System.nanoTime());
		});

		// Hopefully this updates fast enough?
		ClientTickEvents.END_CLIENT_TICK.register((ctx) -> {
			// Remove entries older than the period time
			var currentTime = System.nanoTime();
			while (blocksBroken.size() != 0) {
				var first = blocksBroken.getFirst();
				if (currentTime - first > cooldown * 1e+9)
					blocksBroken.removeFirst();
				else
					break;
			}

			// Count our average block breakage
			int blocksPerPeriod = blocksBroken.size();
			// Set our target vibration
			if (enabled)
				buttplugThread.setTargetVibrate(Math.min((double)blocksPerPeriod / (double)blocks, sensitivity));
			else
				buttplugThread.setTargetVibrate(0.0);
		});
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("bpm")
                .then(literal("enabled")
                        .then(argument("value", bool())
                                .executes(ctx -> {
									enabled = getBool(ctx, "value");
									return Command.SINGLE_SUCCESS;
								} )))
				.then(literal("cooldown")
                        .then(argument("seconds", integer(1, 60))
                                .executes(ctx -> {
									cooldown = getInteger(ctx, "seconds");
									return Command.SINGLE_SUCCESS;
								} )))
				.then(literal("blocks")
                        .then(argument("amount", integer(1))
                                .executes(ctx -> {
									blocks = getInteger(ctx, "amount");
									return Command.SINGLE_SUCCESS;
								} )))
				.then(literal("sensitivity")
								.then(argument("max", doubleArg(0.0, 1.0))
										.executes(ctx -> {
											sensitivity = getDouble(ctx, "max");
											return Command.SINGLE_SUCCESS;
										} )))
				.then(literal("connect")
						.executes(ctx -> {
							buttplugThread.setFeedbackSource(ctx.getSource());
							buttplugThread.tryConnect();
							return Command.SINGLE_SUCCESS;
						} )
						.then(argument("address", string())
							.executes(ctx -> {
								try {
									var address = new URI(getString(ctx, "address"));
									buttplugThread.setFeedbackSource(ctx.getSource());
									buttplugThread.tryConnect(address);
								} catch (URISyntaxException e) {
									throw new SimpleCommandExceptionType(Text.literal("invalid address format")).create(); 
								}
								return Command.SINGLE_SUCCESS;
							} )))
				.then(literal("disconnect")
					.executes(ctx -> {
						buttplugThread.disconnect();
						return Command.SINGLE_SUCCESS;
					} ))
		);
	}
}