package com.simibubi.create.infrastructure.gametest.tests;

import static com.simibubi.create.infrastructure.gametest.CreateGameTestHelper.TEN_SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager.CrossNetworkData;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.infrastructure.gametest.CreateGameTestHelper;
import com.simibubi.create.infrastructure.gametest.GameTestGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;

import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestGroup(path = "regressions")
public class TestRegressions {
	@GameTest(template = "issue9615_efficient_deployers", timeoutTicks = TEN_SECONDS)
	public static void issue9615_efficientDeployers(CreateGameTestHelper helper) {
		final BlockPos lever = new BlockPos(2, 5, 0);
		final BlockPos goal = new BlockPos(1, 3, 4);
		helper.unpowerLever(lever);
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.LIME_STAINED_GLASS, goal));
	}

	@GameTest(template = "logistics_request_completion", timeoutTicks = TEN_SECONDS)
	public static void packageInsertionSimulatesBeforeUnpacking(CreateGameTestHelper helper) {
		ItemStackHandler contents = new ItemStackHandler(PackageItem.SLOTS);
		contents.setStackInSlot(0, new ItemStack(Items.STONE));
		ItemStack box = PackageItem.containing(contents);

		RecordingPackager packager = new RecordingPackager(true);
		ItemStack remainder = packager.inventory.insertItem(0, box, false);

		require(helper, remainder.isEmpty(), "Package insertion was rejected");
		require(helper, packager.unwrapCalls.equals(List.of(true, false)),
			"Package insertion did not simulate the complete unpack first");
		require(helper, packager.stockChecks == 1, "Package insertion did not trigger one stock check");
		helper.succeed();
	}

	@GameTest(template = "logistics_request_completion", timeoutTicks = TEN_SECONDS)
	public static void incompleteCrossNetworkRequestIsDetected(CreateGameTestHelper helper) {
		UUID planksNetwork = UUID.randomUUID();
		UUID slabsNetwork = UUID.randomUUID();
		setupRequestSource(helper, new BlockPos(1, 1, 1), planksNetwork, new ItemStack(Items.OAK_PLANKS, 6));
		setupRequestSource(helper, new BlockPos(5, 1, 1), slabsNetwork, new ItemStack(Items.OAK_SLAB));

		helper.runAfterDelay(2, () -> {
			CrossNetworkData data = new CrossNetworkData();
			LogisticsManager.findPackagersForRequest(planksNetwork,
				PackageOrderWithCrafts.simple(new ArrayList<>(List.of(
					new BigItemStack(new ItemStack(Items.OAK_PLANKS), 6)))),
				null, "test:barrel", data);
			require(helper, data.requestComplete, "Complete request was marked incomplete");

			LogisticsManager.findPackagersForRequest(slabsNetwork,
				PackageOrderWithCrafts.simple(List.of(new BigItemStack(new ItemStack(Items.OAK_SLAB), 2))),
				null, "test:barrel", data);
			require(helper, !data.requestComplete, "Incomplete request was marked complete");
			helper.succeed();
		});
	}

	private static void setupRequestSource(CreateGameTestHelper helper, BlockPos packagerPos, UUID network,
		ItemStack contents) {
		BlockPos chestPos = packagerPos.south();
		BlockPos linkPos = packagerPos.above();
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(packagerPos, AllBlocks.PACKAGER.getDefaultState()
			.setValue(PackagerBlock.FACING, Direction.NORTH)
			.setValue(PackagerBlock.LINKED, true));
		helper.setBlock(linkPos, AllBlocks.STOCK_LINK.getDefaultState()
			.setValue(PackagerLinkBlock.FACE, AttachFace.FLOOR)
			.setValue(PackagerLinkBlock.FACING, Direction.NORTH));

		PackagerLinkBlockEntity link = helper.getBlockEntity(AllBlockEntityTypes.PACKAGER_LINK.get(), linkPos);
		link.behaviour.freqId = network;
		LogisticallyLinkedBehaviour.keepAlive(link.behaviour);
		ItemStack remainder = ItemHandlerHelper.insertItem(helper.itemStorageAt(chestPos), contents, false);
		require(helper, remainder.isEmpty(), "Test inventory rejected its contents");
	}

	private static void require(CreateGameTestHelper helper, boolean condition, String message) {
		if (!condition)
			helper.fail(message);
	}

	private static class RecordingPackager extends PackagerBlockEntity {
		private final boolean simulationResult;
		private final List<Boolean> unwrapCalls = new ArrayList<>();
		private int stockChecks;

		private RecordingPackager(boolean simulationResult) {
			super(AllBlockEntityTypes.PACKAGER.get(), BlockPos.ZERO, AllBlocks.PACKAGER.getDefaultState());
			this.simulationResult = simulationResult;
		}

		@Override
		public boolean unwrapBox(ItemStack box, boolean simulate) {
			unwrapCalls.add(simulate);
			return !simulate || simulationResult;
		}

		@Override
		public void triggerStockCheck() {
			stockChecks++;
		}
	}
}
