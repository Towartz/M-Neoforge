package meteordevelopment.meteorclient.utils.network;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

public class PacketUtils {
   public static final Registry<Class<? extends Packet<?>>> REGISTRY = new PacketUtils.PacketRegistry();
   private static final Map<Class<? extends Packet<?>>, String> S2C_PACKETS = new Reference2ObjectOpenHashMap();
   private static final Map<Class<? extends Packet<?>>, String> C2S_PACKETS = new Reference2ObjectOpenHashMap();
   private static final Map<String, Class<? extends Packet<?>>> S2C_PACKETS_R = new Object2ReferenceOpenHashMap();
   private static final Map<String, Class<? extends Packet<?>>> C2S_PACKETS_R = new Object2ReferenceOpenHashMap();

   private PacketUtils() {
   }

   public static String getName(Class<? extends Packet<?>> packetClass) {
      String name = S2C_PACKETS.get(packetClass);
      return name != null ? name : C2S_PACKETS.get(packetClass);
   }

   public static Class<? extends Packet<?>> getPacket(String name) {
      Class<? extends Packet<?>> packet = S2C_PACKETS_R.get(name);
      return packet != null ? packet : C2S_PACKETS_R.get(name);
   }

   public static Set<Class<? extends Packet<?>>> getS2CPackets() {
      return S2C_PACKETS.keySet();
   }

   public static Set<Class<? extends Packet<?>>> getC2SPackets() {
      return C2S_PACKETS.keySet();
   }

   static {
      C2S_PACKETS.put(ServerboundClientCommandPacket.class, "ClientStatusC2SPacket");
      C2S_PACKETS_R.put("ClientStatusC2SPacket", ServerboundClientCommandPacket.class);
      C2S_PACKETS.put(ServerboundUseItemPacket.class, "PlayerInteractItemC2SPacket");
      C2S_PACKETS_R.put("PlayerInteractItemC2SPacket", ServerboundUseItemPacket.class);
      C2S_PACKETS.put(ServerboundLoginAcknowledgedPacket.class, "EnterConfigurationC2SPacket");
      C2S_PACKETS_R.put("EnterConfigurationC2SPacket", ServerboundLoginAcknowledgedPacket.class);
      C2S_PACKETS.put(ServerboundPlayerActionPacket.class, "PlayerActionC2SPacket");
      C2S_PACKETS_R.put("PlayerActionC2SPacket", ServerboundPlayerActionPacket.class);
      C2S_PACKETS.put(ServerboundSelectTradePacket.class, "SelectMerchantTradeC2SPacket");
      C2S_PACKETS_R.put("SelectMerchantTradeC2SPacket", ServerboundSelectTradePacket.class);
      C2S_PACKETS.put(ServerboundChatCommandPacket.class, "CommandExecutionC2SPacket");
      C2S_PACKETS_R.put("CommandExecutionC2SPacket", ServerboundChatCommandPacket.class);
      C2S_PACKETS.put(ServerboundRenameItemPacket.class, "RenameItemC2SPacket");
      C2S_PACKETS_R.put("RenameItemC2SPacket", ServerboundRenameItemPacket.class);
      C2S_PACKETS.put(ServerboundHelloPacket.class, "LoginHelloC2SPacket");
      C2S_PACKETS_R.put("LoginHelloC2SPacket", ServerboundHelloPacket.class);
      C2S_PACKETS.put(ServerboundUseItemOnPacket.class, "PlayerInteractBlockC2SPacket");
      C2S_PACKETS_R.put("PlayerInteractBlockC2SPacket", ServerboundUseItemOnPacket.class);
      C2S_PACKETS.put(ServerboundBlockEntityTagQueryPacket.class, "QueryBlockNbtC2SPacket");
      C2S_PACKETS_R.put("QueryBlockNbtC2SPacket", ServerboundBlockEntityTagQueryPacket.class);
      C2S_PACKETS.put(ServerboundInteractPacket.class, "PlayerInteractEntityC2SPacket");
      C2S_PACKETS_R.put("PlayerInteractEntityC2SPacket", ServerboundInteractPacket.class);
      C2S_PACKETS.put(ServerboundCommandSuggestionPacket.class, "RequestCommandCompletionsC2SPacket");
      C2S_PACKETS_R.put("RequestCommandCompletionsC2SPacket", ServerboundCommandSuggestionPacket.class);
      C2S_PACKETS.put(ServerboundPlayerAbilitiesPacket.class, "UpdatePlayerAbilitiesC2SPacket");
      C2S_PACKETS_R.put("UpdatePlayerAbilitiesC2SPacket", ServerboundPlayerAbilitiesPacket.class);
      C2S_PACKETS.put(ServerboundConfigurationAcknowledgedPacket.class, "AcknowledgeReconfigurationC2SPacket");
      C2S_PACKETS_R.put("AcknowledgeReconfigurationC2SPacket", ServerboundConfigurationAcknowledgedPacket.class);
      C2S_PACKETS.put(ServerboundStatusRequestPacket.class, "QueryRequestC2SPacket");
      C2S_PACKETS_R.put("QueryRequestC2SPacket", ServerboundStatusRequestPacket.class);
      C2S_PACKETS.put(ServerboundSetCommandBlockPacket.class, "UpdateCommandBlockC2SPacket");
      C2S_PACKETS_R.put("UpdateCommandBlockC2SPacket", ServerboundSetCommandBlockPacket.class);
      C2S_PACKETS.put(ServerboundSwingPacket.class, "HandSwingC2SPacket");
      C2S_PACKETS_R.put("HandSwingC2SPacket", ServerboundSwingPacket.class);
      C2S_PACKETS.put(ServerboundSeenAdvancementsPacket.class, "AdvancementTabC2SPacket");
      C2S_PACKETS_R.put("AdvancementTabC2SPacket", ServerboundSeenAdvancementsPacket.class);
      C2S_PACKETS.put(ServerboundContainerClickPacket.class, "ClickSlotC2SPacket");
      C2S_PACKETS_R.put("ClickSlotC2SPacket", ServerboundContainerClickPacket.class);
      C2S_PACKETS.put(ServerboundChunkBatchReceivedPacket.class, "AcknowledgeChunksC2SPacket");
      C2S_PACKETS_R.put("AcknowledgeChunksC2SPacket", ServerboundChunkBatchReceivedPacket.class);
      C2S_PACKETS.put(ServerboundTeleportToEntityPacket.class, "SpectatorTeleportC2SPacket");
      C2S_PACKETS_R.put("SpectatorTeleportC2SPacket", ServerboundTeleportToEntityPacket.class);
      C2S_PACKETS.put(ServerboundKeyPacket.class, "LoginKeyC2SPacket");
      C2S_PACKETS_R.put("LoginKeyC2SPacket", ServerboundKeyPacket.class);
      C2S_PACKETS.put(ServerboundLockDifficultyPacket.class, "UpdateDifficultyLockC2SPacket");
      C2S_PACKETS_R.put("UpdateDifficultyLockC2SPacket", ServerboundLockDifficultyPacket.class);
      C2S_PACKETS.put(ServerboundJigsawGeneratePacket.class, "JigsawGeneratingC2SPacket");
      C2S_PACKETS_R.put("JigsawGeneratingC2SPacket", ServerboundJigsawGeneratePacket.class);
      C2S_PACKETS.put(ServerboundEntityTagQueryPacket.class, "QueryEntityNbtC2SPacket");
      C2S_PACKETS_R.put("QueryEntityNbtC2SPacket", ServerboundEntityTagQueryPacket.class);
      C2S_PACKETS.put(ServerboundSetCarriedItemPacket.class, "UpdateSelectedSlotC2SPacket");
      C2S_PACKETS_R.put("UpdateSelectedSlotC2SPacket", ServerboundSetCarriedItemPacket.class);
      C2S_PACKETS.put(ServerboundRecipeBookChangeSettingsPacket.class, "RecipeCategoryOptionsC2SPacket");
      C2S_PACKETS_R.put("RecipeCategoryOptionsC2SPacket", ServerboundRecipeBookChangeSettingsPacket.class);
      C2S_PACKETS.put(ServerboundResourcePackPacket.class, "ResourcePackStatusC2SPacket");
      C2S_PACKETS_R.put("ResourcePackStatusC2SPacket", ServerboundResourcePackPacket.class);
      C2S_PACKETS.put(ServerboundMovePlayerPacket.class, "PlayerMoveC2SPacket");
      C2S_PACKETS_R.put("PlayerMoveC2SPacket", ServerboundMovePlayerPacket.class);
      C2S_PACKETS.put(ServerboundClientInformationPacket.class, "ClientOptionsC2SPacket");
      C2S_PACKETS_R.put("ClientOptionsC2SPacket", ServerboundClientInformationPacket.class);
      C2S_PACKETS.put(ServerboundCustomPayloadPacket.class, "CustomPayloadC2SPacket");
      C2S_PACKETS_R.put("CustomPayloadC2SPacket", ServerboundCustomPayloadPacket.class);
      C2S_PACKETS.put(ServerboundPickItemPacket.class, "PickFromInventoryC2SPacket");
      C2S_PACKETS_R.put("PickFromInventoryC2SPacket", ServerboundPickItemPacket.class);
      C2S_PACKETS.put(ServerboundChatSessionUpdatePacket.class, "PlayerSessionC2SPacket");
      C2S_PACKETS_R.put("PlayerSessionC2SPacket", ServerboundChatSessionUpdatePacket.class);
      C2S_PACKETS.put(ServerboundContainerClosePacket.class, "CloseHandledScreenC2SPacket");
      C2S_PACKETS_R.put("CloseHandledScreenC2SPacket", ServerboundContainerClosePacket.class);
      C2S_PACKETS.put(ServerboundChatCommandSignedPacket.class, "ChatCommandSignedC2SPacket");
      C2S_PACKETS_R.put("ChatCommandSignedC2SPacket", ServerboundChatCommandSignedPacket.class);
      C2S_PACKETS.put(ServerboundFinishConfigurationPacket.class, "ReadyC2SPacket");
      C2S_PACKETS_R.put("ReadyC2SPacket", ServerboundFinishConfigurationPacket.class);
      C2S_PACKETS.put(ServerboundContainerSlotStateChangedPacket.class, "SlotChangedStateC2SPacket");
      C2S_PACKETS_R.put("SlotChangedStateC2SPacket", ServerboundContainerSlotStateChangedPacket.class);
      C2S_PACKETS.put(ServerboundPaddleBoatPacket.class, "BoatPaddleStateC2SPacket");
      C2S_PACKETS_R.put("BoatPaddleStateC2SPacket", ServerboundPaddleBoatPacket.class);
      C2S_PACKETS.put(ServerboundContainerButtonClickPacket.class, "ButtonClickC2SPacket");
      C2S_PACKETS_R.put("ButtonClickC2SPacket", ServerboundContainerButtonClickPacket.class);
      C2S_PACKETS.put(ServerboundSelectKnownPacks.class, "SelectKnownPacksC2SPacket");
      C2S_PACKETS_R.put("SelectKnownPacksC2SPacket", ServerboundSelectKnownPacks.class);
      C2S_PACKETS.put(ServerboundChatPacket.class, "ChatMessageC2SPacket");
      C2S_PACKETS_R.put("ChatMessageC2SPacket", ServerboundChatPacket.class);
      C2S_PACKETS.put(ServerboundSetBeaconPacket.class, "UpdateBeaconC2SPacket");
      C2S_PACKETS_R.put("UpdateBeaconC2SPacket", ServerboundSetBeaconPacket.class);
      C2S_PACKETS.put(ServerboundSignUpdatePacket.class, "UpdateSignC2SPacket");
      C2S_PACKETS_R.put("UpdateSignC2SPacket", ServerboundSignUpdatePacket.class);
      C2S_PACKETS.put(ServerboundAcceptTeleportationPacket.class, "TeleportConfirmC2SPacket");
      C2S_PACKETS_R.put("TeleportConfirmC2SPacket", ServerboundAcceptTeleportationPacket.class);
      C2S_PACKETS.put(ServerboundSetStructureBlockPacket.class, "UpdateStructureBlockC2SPacket");
      C2S_PACKETS_R.put("UpdateStructureBlockC2SPacket", ServerboundSetStructureBlockPacket.class);
      C2S_PACKETS.put(ServerboundSetCommandMinecartPacket.class, "UpdateCommandBlockMinecartC2SPacket");
      C2S_PACKETS_R.put("UpdateCommandBlockMinecartC2SPacket", ServerboundSetCommandMinecartPacket.class);
      C2S_PACKETS.put(ServerboundPongPacket.class, "CommonPongC2SPacket");
      C2S_PACKETS_R.put("CommonPongC2SPacket", ServerboundPongPacket.class);
      C2S_PACKETS.put(ServerboundPlayerInputPacket.class, "PlayerInputC2SPacket");
      C2S_PACKETS_R.put("PlayerInputC2SPacket", ServerboundPlayerInputPacket.class);
      C2S_PACKETS.put(ServerboundPlayerCommandPacket.class, "ClientCommandC2SPacket");
      C2S_PACKETS_R.put("ClientCommandC2SPacket", ServerboundPlayerCommandPacket.class);
      C2S_PACKETS.put(ServerboundSetJigsawBlockPacket.class, "UpdateJigsawC2SPacket");
      C2S_PACKETS_R.put("UpdateJigsawC2SPacket", ServerboundSetJigsawBlockPacket.class);
      C2S_PACKETS.put(ServerboundPingRequestPacket.class, "QueryPingC2SPacket");
      C2S_PACKETS_R.put("QueryPingC2SPacket", ServerboundPingRequestPacket.class);
      C2S_PACKETS.put(ServerboundCookieResponsePacket.class, "CookieResponseC2SPacket");
      C2S_PACKETS_R.put("CookieResponseC2SPacket", ServerboundCookieResponsePacket.class);
      C2S_PACKETS.put(ServerboundChatAckPacket.class, "MessageAcknowledgmentC2SPacket");
      C2S_PACKETS_R.put("MessageAcknowledgmentC2SPacket", ServerboundChatAckPacket.class);
      C2S_PACKETS.put(ServerboundDebugSampleSubscriptionPacket.class, "DebugSampleSubscriptionC2SPacket");
      C2S_PACKETS_R.put("DebugSampleSubscriptionC2SPacket", ServerboundDebugSampleSubscriptionPacket.class);
      C2S_PACKETS.put(ServerboundKeepAlivePacket.class, "KeepAliveC2SPacket");
      C2S_PACKETS_R.put("KeepAliveC2SPacket", ServerboundKeepAlivePacket.class);
      C2S_PACKETS.put(ServerboundSetCreativeModeSlotPacket.class, "CreativeInventoryActionC2SPacket");
      C2S_PACKETS_R.put("CreativeInventoryActionC2SPacket", ServerboundSetCreativeModeSlotPacket.class);
      C2S_PACKETS.put(ServerboundMoveVehiclePacket.class, "VehicleMoveC2SPacket");
      C2S_PACKETS_R.put("VehicleMoveC2SPacket", ServerboundMoveVehiclePacket.class);
      C2S_PACKETS.put(ServerboundEditBookPacket.class, "BookUpdateC2SPacket");
      C2S_PACKETS_R.put("BookUpdateC2SPacket", ServerboundEditBookPacket.class);
      C2S_PACKETS.put(ServerboundRecipeBookSeenRecipePacket.class, "RecipeBookDataC2SPacket");
      C2S_PACKETS_R.put("RecipeBookDataC2SPacket", ServerboundRecipeBookSeenRecipePacket.class);
      C2S_PACKETS.put(ClientIntentionPacket.class, "HandshakeC2SPacket");
      C2S_PACKETS_R.put("HandshakeC2SPacket", ClientIntentionPacket.class);
      C2S_PACKETS.put(ServerboundCustomQueryAnswerPacket.class, "LoginQueryResponseC2SPacket");
      C2S_PACKETS_R.put("LoginQueryResponseC2SPacket", ServerboundCustomQueryAnswerPacket.class);
      C2S_PACKETS.put(ServerboundChangeDifficultyPacket.class, "UpdateDifficultyC2SPacket");
      C2S_PACKETS_R.put("UpdateDifficultyC2SPacket", ServerboundChangeDifficultyPacket.class);
      C2S_PACKETS.put(ServerboundPlaceRecipePacket.class, "CraftRequestC2SPacket");
      C2S_PACKETS_R.put("CraftRequestC2SPacket", ServerboundPlaceRecipePacket.class);
      C2S_PACKETS.put(Rot.class, "PlayerMoveC2SPacket.LookAndOnGround");
      C2S_PACKETS_R.put("PlayerMoveC2SPacket.LookAndOnGround", Rot.class);
      C2S_PACKETS.put(StatusOnly.class, "PlayerMoveC2SPacket.OnGroundOnly");
      C2S_PACKETS_R.put("PlayerMoveC2SPacket.OnGroundOnly", StatusOnly.class);
      C2S_PACKETS.put(PosRot.class, "PlayerMoveC2SPacket.Full");
      C2S_PACKETS_R.put("PlayerMoveC2SPacket.Full", PosRot.class);
      C2S_PACKETS.put(Pos.class, "PlayerMoveC2SPacket.PositionAndOnGround");
      C2S_PACKETS_R.put("PlayerMoveC2SPacket.PositionAndOnGround", Pos.class);
      S2C_PACKETS.put(ClientboundSetBorderSizePacket.class, "WorldBorderSizeChangedS2CPacket");
      S2C_PACKETS_R.put("WorldBorderSizeChangedS2CPacket", ClientboundSetBorderSizePacket.class);
      S2C_PACKETS.put(ClientboundUpdateAdvancementsPacket.class, "AdvancementUpdateS2CPacket");
      S2C_PACKETS_R.put("AdvancementUpdateS2CPacket", ClientboundUpdateAdvancementsPacket.class);
      S2C_PACKETS.put(ClientboundSetBorderLerpSizePacket.class, "WorldBorderInterpolateSizeS2CPacket");
      S2C_PACKETS_R.put("WorldBorderInterpolateSizeS2CPacket", ClientboundSetBorderLerpSizePacket.class);
      S2C_PACKETS.put(ClientboundSetChunkCacheRadiusPacket.class, "ChunkLoadDistanceS2CPacket");
      S2C_PACKETS_R.put("ChunkLoadDistanceS2CPacket", ClientboundSetChunkCacheRadiusPacket.class);
      S2C_PACKETS.put(ClientboundTakeItemEntityPacket.class, "ItemPickupAnimationS2CPacket");
      S2C_PACKETS_R.put("ItemPickupAnimationS2CPacket", ClientboundTakeItemEntityPacket.class);
      S2C_PACKETS.put(ClientboundRespawnPacket.class, "PlayerRespawnS2CPacket");
      S2C_PACKETS_R.put("PlayerRespawnS2CPacket", ClientboundRespawnPacket.class);
      S2C_PACKETS.put(ClientboundTabListPacket.class, "PlayerListHeaderS2CPacket");
      S2C_PACKETS_R.put("PlayerListHeaderS2CPacket", ClientboundTabListPacket.class);
      S2C_PACKETS.put(ClientboundAddEntityPacket.class, "EntitySpawnS2CPacket");
      S2C_PACKETS_R.put("EntitySpawnS2CPacket", ClientboundAddEntityPacket.class);
      S2C_PACKETS.put(ClientboundDeleteChatPacket.class, "RemoveMessageS2CPacket");
      S2C_PACKETS_R.put("RemoveMessageS2CPacket", ClientboundDeleteChatPacket.class);
      S2C_PACKETS.put(ClientboundSetCameraPacket.class, "SetCameraEntityS2CPacket");
      S2C_PACKETS_R.put("SetCameraEntityS2CPacket", ClientboundSetCameraPacket.class);
      S2C_PACKETS.put(ClientboundResourcePackPushPacket.class, "ResourcePackSendS2CPacket");
      S2C_PACKETS_R.put("ResourcePackSendS2CPacket", ClientboundResourcePackPushPacket.class);
      S2C_PACKETS.put(ClientboundHurtAnimationPacket.class, "DamageTiltS2CPacket");
      S2C_PACKETS_R.put("DamageTiltS2CPacket", ClientboundHurtAnimationPacket.class);
      S2C_PACKETS.put(ClientboundPlaceGhostRecipePacket.class, "CraftFailedResponseS2CPacket");
      S2C_PACKETS_R.put("CraftFailedResponseS2CPacket", ClientboundPlaceGhostRecipePacket.class);
      S2C_PACKETS.put(ClientboundAwardStatsPacket.class, "StatisticsS2CPacket");
      S2C_PACKETS_R.put("StatisticsS2CPacket", ClientboundAwardStatsPacket.class);
      S2C_PACKETS.put(ClientboundCustomQueryPacket.class, "LoginQueryRequestS2CPacket");
      S2C_PACKETS_R.put("LoginQueryRequestS2CPacket", ClientboundCustomQueryPacket.class);
      S2C_PACKETS.put(ClientboundMoveVehiclePacket.class, "VehicleMoveS2CPacket");
      S2C_PACKETS_R.put("VehicleMoveS2CPacket", ClientboundMoveVehiclePacket.class);
      S2C_PACKETS.put(ClientboundUpdateAttributesPacket.class, "EntityAttributesS2CPacket");
      S2C_PACKETS_R.put("EntityAttributesS2CPacket", ClientboundUpdateAttributesPacket.class);
      S2C_PACKETS.put(ClientboundTickingStepPacket.class, "TickStepS2CPacket");
      S2C_PACKETS_R.put("TickStepS2CPacket", ClientboundTickingStepPacket.class);
      S2C_PACKETS.put(ClientboundStopSoundPacket.class, "StopSoundS2CPacket");
      S2C_PACKETS_R.put("StopSoundS2CPacket", ClientboundStopSoundPacket.class);
      S2C_PACKETS.put(ClientboundSetObjectivePacket.class, "ScoreboardObjectiveUpdateS2CPacket");
      S2C_PACKETS_R.put("ScoreboardObjectiveUpdateS2CPacket", ClientboundSetObjectivePacket.class);
      S2C_PACKETS.put(ClientboundChunkBatchStartPacket.class, "StartChunkSendS2CPacket");
      S2C_PACKETS_R.put("StartChunkSendS2CPacket", ClientboundChunkBatchStartPacket.class);
      S2C_PACKETS.put(ClientboundCookieRequestPacket.class, "CookieRequestS2CPacket");
      S2C_PACKETS_R.put("CookieRequestS2CPacket", ClientboundCookieRequestPacket.class);
      S2C_PACKETS.put(ClientboundRotateHeadPacket.class, "EntitySetHeadYawS2CPacket");
      S2C_PACKETS_R.put("EntitySetHeadYawS2CPacket", ClientboundRotateHeadPacket.class);
      S2C_PACKETS.put(ClientboundSelectAdvancementsTabPacket.class, "SelectAdvancementTabS2CPacket");
      S2C_PACKETS_R.put("SelectAdvancementTabS2CPacket", ClientboundSelectAdvancementsTabPacket.class);
      S2C_PACKETS.put(ClientboundMerchantOffersPacket.class, "SetTradeOffersS2CPacket");
      S2C_PACKETS_R.put("SetTradeOffersS2CPacket", ClientboundMerchantOffersPacket.class);
      S2C_PACKETS.put(ClientboundStoreCookiePacket.class, "StoreCookieS2CPacket");
      S2C_PACKETS_R.put("StoreCookieS2CPacket", ClientboundStoreCookiePacket.class);
      S2C_PACKETS.put(ClientboundSoundPacket.class, "PlaySoundS2CPacket");
      S2C_PACKETS_R.put("PlaySoundS2CPacket", ClientboundSoundPacket.class);
      S2C_PACKETS.put(ClientboundBlockEventPacket.class, "BlockEventS2CPacket");
      S2C_PACKETS_R.put("BlockEventS2CPacket", ClientboundBlockEventPacket.class);
      S2C_PACKETS.put(ClientboundPlayerPositionPacket.class, "PlayerPositionLookS2CPacket");
      S2C_PACKETS_R.put("PlayerPositionLookS2CPacket", ClientboundPlayerPositionPacket.class);
      S2C_PACKETS.put(ClientboundChunkBatchFinishedPacket.class, "ChunkSentS2CPacket");
      S2C_PACKETS_R.put("ChunkSentS2CPacket", ClientboundChunkBatchFinishedPacket.class);
      S2C_PACKETS.put(ClientboundSetHealthPacket.class, "HealthUpdateS2CPacket");
      S2C_PACKETS_R.put("HealthUpdateS2CPacket", ClientboundSetHealthPacket.class);
      S2C_PACKETS.put(ClientboundLevelEventPacket.class, "WorldEventS2CPacket");
      S2C_PACKETS_R.put("WorldEventS2CPacket", ClientboundLevelEventPacket.class);
      S2C_PACKETS.put(ClientboundUpdateTagsPacket.class, "SynchronizeTagsS2CPacket");
      S2C_PACKETS_R.put("SynchronizeTagsS2CPacket", ClientboundUpdateTagsPacket.class);
      S2C_PACKETS.put(ClientboundServerDataPacket.class, "ServerMetadataS2CPacket");
      S2C_PACKETS_R.put("ServerMetadataS2CPacket", ClientboundServerDataPacket.class);
      S2C_PACKETS.put(ClientboundSetCarriedItemPacket.class, "UpdateSelectedSlotS2CPacket");
      S2C_PACKETS_R.put("UpdateSelectedSlotS2CPacket", ClientboundSetCarriedItemPacket.class);
      S2C_PACKETS.put(ClientboundSectionBlocksUpdatePacket.class, "ChunkDeltaUpdateS2CPacket");
      S2C_PACKETS_R.put("ChunkDeltaUpdateS2CPacket", ClientboundSectionBlocksUpdatePacket.class);
      S2C_PACKETS.put(ClientboundStatusResponsePacket.class, "QueryResponseS2CPacket");
      S2C_PACKETS_R.put("QueryResponseS2CPacket", ClientboundStatusResponsePacket.class);
      S2C_PACKETS.put(ClientboundSetPlayerTeamPacket.class, "TeamS2CPacket");
      S2C_PACKETS_R.put("TeamS2CPacket", ClientboundSetPlayerTeamPacket.class);
      S2C_PACKETS.put(ClientboundCooldownPacket.class, "CooldownUpdateS2CPacket");
      S2C_PACKETS_R.put("CooldownUpdateS2CPacket", ClientboundCooldownPacket.class);
      S2C_PACKETS.put(ClientboundOpenScreenPacket.class, "OpenScreenS2CPacket");
      S2C_PACKETS_R.put("OpenScreenS2CPacket", ClientboundOpenScreenPacket.class);
      S2C_PACKETS.put(ClientboundAddExperienceOrbPacket.class, "ExperienceOrbSpawnS2CPacket");
      S2C_PACKETS_R.put("ExperienceOrbSpawnS2CPacket", ClientboundAddExperienceOrbPacket.class);
      S2C_PACKETS.put(ClientboundAnimatePacket.class, "EntityAnimationS2CPacket");
      S2C_PACKETS_R.put("EntityAnimationS2CPacket", ClientboundAnimatePacket.class);
      S2C_PACKETS.put(ClientboundPlayerAbilitiesPacket.class, "PlayerAbilitiesS2CPacket");
      S2C_PACKETS_R.put("PlayerAbilitiesS2CPacket", ClientboundPlayerAbilitiesPacket.class);
      S2C_PACKETS.put(ClientboundResetChatPacket.class, "ResetChatS2CPacket");
      S2C_PACKETS_R.put("ResetChatS2CPacket", ClientboundResetChatPacket.class);
      S2C_PACKETS.put(ClientboundSetBorderWarningDistancePacket.class, "WorldBorderWarningBlocksChangedS2CPacket");
      S2C_PACKETS_R.put("WorldBorderWarningBlocksChangedS2CPacket", ClientboundSetBorderWarningDistancePacket.class);
      S2C_PACKETS.put(ClientboundRemoveEntitiesPacket.class, "EntitiesDestroyS2CPacket");
      S2C_PACKETS_R.put("EntitiesDestroyS2CPacket", ClientboundRemoveEntitiesPacket.class);
      S2C_PACKETS.put(ClientboundPlayerInfoRemovePacket.class, "PlayerRemoveS2CPacket");
      S2C_PACKETS_R.put("PlayerRemoveS2CPacket", ClientboundPlayerInfoRemovePacket.class);
      S2C_PACKETS.put(ClientboundLightUpdatePacket.class, "LightUpdateS2CPacket");
      S2C_PACKETS_R.put("LightUpdateS2CPacket", ClientboundLightUpdatePacket.class);
      S2C_PACKETS.put(ClientboundSetActionBarTextPacket.class, "OverlayMessageS2CPacket");
      S2C_PACKETS_R.put("OverlayMessageS2CPacket", ClientboundSetActionBarTextPacket.class);
      S2C_PACKETS.put(ClientboundInitializeBorderPacket.class, "WorldBorderInitializeS2CPacket");
      S2C_PACKETS_R.put("WorldBorderInitializeS2CPacket", ClientboundInitializeBorderPacket.class);
      S2C_PACKETS.put(ClientboundSetBorderCenterPacket.class, "WorldBorderCenterChangedS2CPacket");
      S2C_PACKETS_R.put("WorldBorderCenterChangedS2CPacket", ClientboundSetBorderCenterPacket.class);
      S2C_PACKETS.put(ClientboundSetEntityMotionPacket.class, "EntityVelocityUpdateS2CPacket");
      S2C_PACKETS_R.put("EntityVelocityUpdateS2CPacket", ClientboundSetEntityMotionPacket.class);
      S2C_PACKETS.put(ClientboundChangeDifficultyPacket.class, "DifficultyS2CPacket");
      S2C_PACKETS_R.put("DifficultyS2CPacket", ClientboundChangeDifficultyPacket.class);
      S2C_PACKETS.put(ClientboundPlayerLookAtPacket.class, "LookAtS2CPacket");
      S2C_PACKETS_R.put("LookAtS2CPacket", ClientboundPlayerLookAtPacket.class);
      S2C_PACKETS.put(ClientboundSetScorePacket.class, "ScoreboardScoreUpdateS2CPacket");
      S2C_PACKETS_R.put("ScoreboardScoreUpdateS2CPacket", ClientboundSetScorePacket.class);
      S2C_PACKETS.put(ClientboundSetTitleTextPacket.class, "TitleS2CPacket");
      S2C_PACKETS_R.put("TitleS2CPacket", ClientboundSetTitleTextPacket.class);
      S2C_PACKETS.put(ClientboundContainerSetDataPacket.class, "ScreenHandlerPropertyUpdateS2CPacket");
      S2C_PACKETS_R.put("ScreenHandlerPropertyUpdateS2CPacket", ClientboundContainerSetDataPacket.class);
      S2C_PACKETS.put(ClientboundHorseScreenOpenPacket.class, "OpenHorseScreenS2CPacket");
      S2C_PACKETS_R.put("OpenHorseScreenS2CPacket", ClientboundHorseScreenOpenPacket.class);
      S2C_PACKETS.put(ClientboundSetSimulationDistancePacket.class, "SimulationDistanceS2CPacket");
      S2C_PACKETS_R.put("SimulationDistanceS2CPacket", ClientboundSetSimulationDistancePacket.class);
      S2C_PACKETS.put(ClientboundCustomChatCompletionsPacket.class, "ChatSuggestionsS2CPacket");
      S2C_PACKETS_R.put("ChatSuggestionsS2CPacket", ClientboundCustomChatCompletionsPacket.class);
      S2C_PACKETS.put(ClientboundPlayerCombatEnterPacket.class, "EnterCombatS2CPacket");
      S2C_PACKETS_R.put("EnterCombatS2CPacket", ClientboundPlayerCombatEnterPacket.class);
      S2C_PACKETS.put(ClientboundDisguisedChatPacket.class, "ProfilelessChatMessageS2CPacket");
      S2C_PACKETS_R.put("ProfilelessChatMessageS2CPacket", ClientboundDisguisedChatPacket.class);
      S2C_PACKETS.put(ClientboundPlayerCombatKillPacket.class, "DeathMessageS2CPacket");
      S2C_PACKETS_R.put("DeathMessageS2CPacket", ClientboundPlayerCombatKillPacket.class);
      S2C_PACKETS.put(ClientboundMapItemDataPacket.class, "MapUpdateS2CPacket");
      S2C_PACKETS_R.put("MapUpdateS2CPacket", ClientboundMapItemDataPacket.class);
      S2C_PACKETS.put(ClientboundContainerSetSlotPacket.class, "ScreenHandlerSlotUpdateS2CPacket");
      S2C_PACKETS_R.put("ScreenHandlerSlotUpdateS2CPacket", ClientboundContainerSetSlotPacket.class);
      S2C_PACKETS.put(ClientboundBlockEntityDataPacket.class, "BlockEntityUpdateS2CPacket");
      S2C_PACKETS_R.put("BlockEntityUpdateS2CPacket", ClientboundBlockEntityDataPacket.class);
      S2C_PACKETS.put(ClientboundSetDefaultSpawnPositionPacket.class, "PlayerSpawnPositionS2CPacket");
      S2C_PACKETS_R.put("PlayerSpawnPositionS2CPacket", ClientboundSetDefaultSpawnPositionPacket.class);
      S2C_PACKETS.put(ClientboundUpdateMobEffectPacket.class, "EntityStatusEffectS2CPacket");
      S2C_PACKETS_R.put("EntityStatusEffectS2CPacket", ClientboundUpdateMobEffectPacket.class);
      S2C_PACKETS.put(ClientboundCustomReportDetailsPacket.class, "CustomReportDetailsS2CPacket");
      S2C_PACKETS_R.put("CustomReportDetailsS2CPacket", ClientboundCustomReportDetailsPacket.class);
      S2C_PACKETS.put(ClientboundClearTitlesPacket.class, "ClearTitleS2CPacket");
      S2C_PACKETS_R.put("ClearTitleS2CPacket", ClientboundClearTitlesPacket.class);
      S2C_PACKETS.put(ClientboundLoginCompressionPacket.class, "LoginCompressionS2CPacket");
      S2C_PACKETS_R.put("LoginCompressionS2CPacket", ClientboundLoginCompressionPacket.class);
      S2C_PACKETS.put(ClientboundCommandsPacket.class, "CommandTreeS2CPacket");
      S2C_PACKETS_R.put("CommandTreeS2CPacket", ClientboundCommandsPacket.class);
      S2C_PACKETS.put(ClientboundPingPacket.class, "CommonPingS2CPacket");
      S2C_PACKETS_R.put("CommonPingS2CPacket", ClientboundPingPacket.class);
      S2C_PACKETS.put(ClientboundResetScorePacket.class, "ScoreboardScoreResetS2CPacket");
      S2C_PACKETS_R.put("ScoreboardScoreResetS2CPacket", ClientboundResetScorePacket.class);
      S2C_PACKETS.put(ClientboundSetTitlesAnimationPacket.class, "TitleFadeS2CPacket");
      S2C_PACKETS_R.put("TitleFadeS2CPacket", ClientboundSetTitlesAnimationPacket.class);
      S2C_PACKETS.put(ClientboundPongResponsePacket.class, "PingResultS2CPacket");
      S2C_PACKETS_R.put("PingResultS2CPacket", ClientboundPongResponsePacket.class);
      S2C_PACKETS.put(ClientboundTickingStatePacket.class, "UpdateTickRateS2CPacket");
      S2C_PACKETS_R.put("UpdateTickRateS2CPacket", ClientboundTickingStatePacket.class);
      S2C_PACKETS.put(ClientboundContainerSetContentPacket.class, "InventoryS2CPacket");
      S2C_PACKETS_R.put("InventoryS2CPacket", ClientboundContainerSetContentPacket.class);
      S2C_PACKETS.put(ClientboundBlockChangedAckPacket.class, "PlayerActionResponseS2CPacket");
      S2C_PACKETS_R.put("PlayerActionResponseS2CPacket", ClientboundBlockChangedAckPacket.class);
      S2C_PACKETS.put(ClientboundDebugSamplePacket.class, "DebugSampleS2CPacket");
      S2C_PACKETS_R.put("DebugSampleS2CPacket", ClientboundDebugSamplePacket.class);
      S2C_PACKETS.put(ClientboundChunksBiomesPacket.class, "ChunkBiomeDataS2CPacket");
      S2C_PACKETS_R.put("ChunkBiomeDataS2CPacket", ClientboundChunksBiomesPacket.class);
      S2C_PACKETS.put(ClientboundSetBorderWarningDelayPacket.class, "WorldBorderWarningTimeChangedS2CPacket");
      S2C_PACKETS_R.put("WorldBorderWarningTimeChangedS2CPacket", ClientboundSetBorderWarningDelayPacket.class);
      S2C_PACKETS.put(ClientboundDamageEventPacket.class, "EntityDamageS2CPacket");
      S2C_PACKETS_R.put("EntityDamageS2CPacket", ClientboundDamageEventPacket.class);
      S2C_PACKETS.put(ClientboundPlayerChatPacket.class, "ChatMessageS2CPacket");
      S2C_PACKETS_R.put("ChatMessageS2CPacket", ClientboundPlayerChatPacket.class);
      S2C_PACKETS.put(ClientboundServerLinksPacket.class, "ServerLinksS2CPacket");
      S2C_PACKETS_R.put("ServerLinksS2CPacket", ClientboundServerLinksPacket.class);
      S2C_PACKETS.put(ClientboundKeepAlivePacket.class, "KeepAliveS2CPacket");
      S2C_PACKETS_R.put("KeepAliveS2CPacket", ClientboundKeepAlivePacket.class);
      S2C_PACKETS.put(ClientboundOpenBookPacket.class, "OpenWrittenBookS2CPacket");
      S2C_PACKETS_R.put("OpenWrittenBookS2CPacket", ClientboundOpenBookPacket.class);
      S2C_PACKETS.put(ClientboundRecipePacket.class, "ChangeUnlockedRecipesS2CPacket");
      S2C_PACKETS_R.put("ChangeUnlockedRecipesS2CPacket", ClientboundRecipePacket.class);
      S2C_PACKETS.put(ClientboundResourcePackPopPacket.class, "ResourcePackRemoveS2CPacket");
      S2C_PACKETS_R.put("ResourcePackRemoveS2CPacket", ClientboundResourcePackPopPacket.class);
      S2C_PACKETS.put(ClientboundSoundEntityPacket.class, "PlaySoundFromEntityS2CPacket");
      S2C_PACKETS_R.put("PlaySoundFromEntityS2CPacket", ClientboundSoundEntityPacket.class);
      S2C_PACKETS.put(ClientboundSetTimePacket.class, "WorldTimeUpdateS2CPacket");
      S2C_PACKETS_R.put("WorldTimeUpdateS2CPacket", ClientboundSetTimePacket.class);
      S2C_PACKETS.put(ClientboundUpdateEnabledFeaturesPacket.class, "FeaturesS2CPacket");
      S2C_PACKETS_R.put("FeaturesS2CPacket", ClientboundUpdateEnabledFeaturesPacket.class);
      S2C_PACKETS.put(ClientboundOpenSignEditorPacket.class, "SignEditorOpenS2CPacket");
      S2C_PACKETS_R.put("SignEditorOpenS2CPacket", ClientboundOpenSignEditorPacket.class);
      S2C_PACKETS.put(ClientboundExplodePacket.class, "ExplosionS2CPacket");
      S2C_PACKETS_R.put("ExplosionS2CPacket", ClientboundExplodePacket.class);
      S2C_PACKETS.put(ClientboundLoginDisconnectPacket.class, "LoginDisconnectS2CPacket");
      S2C_PACKETS_R.put("LoginDisconnectS2CPacket", ClientboundLoginDisconnectPacket.class);
      S2C_PACKETS.put(ClientboundRemoveMobEffectPacket.class, "RemoveEntityStatusEffectS2CPacket");
      S2C_PACKETS_R.put("RemoveEntityStatusEffectS2CPacket", ClientboundRemoveMobEffectPacket.class);
      S2C_PACKETS.put(ClientboundPlayerCombatEndPacket.class, "EndCombatS2CPacket");
      S2C_PACKETS_R.put("EndCombatS2CPacket", ClientboundPlayerCombatEndPacket.class);
      S2C_PACKETS.put(ClientboundPlayerInfoUpdatePacket.class, "PlayerListS2CPacket");
      S2C_PACKETS_R.put("PlayerListS2CPacket", ClientboundPlayerInfoUpdatePacket.class);
      S2C_PACKETS.put(ClientboundSetChunkCacheCenterPacket.class, "ChunkRenderDistanceCenterS2CPacket");
      S2C_PACKETS_R.put("ChunkRenderDistanceCenterS2CPacket", ClientboundSetChunkCacheCenterPacket.class);
      S2C_PACKETS.put(ClientboundSetExperiencePacket.class, "ExperienceBarUpdateS2CPacket");
      S2C_PACKETS_R.put("ExperienceBarUpdateS2CPacket", ClientboundSetExperiencePacket.class);
      S2C_PACKETS.put(ClientboundBlockUpdatePacket.class, "BlockUpdateS2CPacket");
      S2C_PACKETS_R.put("BlockUpdateS2CPacket", ClientboundBlockUpdatePacket.class);
      S2C_PACKETS.put(ClientboundCommandSuggestionsPacket.class, "CommandSuggestionsS2CPacket");
      S2C_PACKETS_R.put("CommandSuggestionsS2CPacket", ClientboundCommandSuggestionsPacket.class);
      S2C_PACKETS.put(ClientboundLevelParticlesPacket.class, "ParticleS2CPacket");
      S2C_PACKETS_R.put("ParticleS2CPacket", ClientboundLevelParticlesPacket.class);
      S2C_PACKETS.put(ClientboundContainerClosePacket.class, "CloseScreenS2CPacket");
      S2C_PACKETS_R.put("CloseScreenS2CPacket", ClientboundContainerClosePacket.class);
      S2C_PACKETS.put(ClientboundSetDisplayObjectivePacket.class, "ScoreboardDisplayS2CPacket");
      S2C_PACKETS_R.put("ScoreboardDisplayS2CPacket", ClientboundSetDisplayObjectivePacket.class);
      S2C_PACKETS.put(ClientboundGameProfilePacket.class, "LoginSuccessS2CPacket");
      S2C_PACKETS_R.put("LoginSuccessS2CPacket", ClientboundGameProfilePacket.class);
      S2C_PACKETS.put(ClientboundBlockDestructionPacket.class, "BlockBreakingProgressS2CPacket");
      S2C_PACKETS_R.put("BlockBreakingProgressS2CPacket", ClientboundBlockDestructionPacket.class);
      S2C_PACKETS.put(ClientboundDisconnectPacket.class, "DisconnectS2CPacket");
      S2C_PACKETS_R.put("DisconnectS2CPacket", ClientboundDisconnectPacket.class);
      S2C_PACKETS.put(ClientboundSystemChatPacket.class, "GameMessageS2CPacket");
      S2C_PACKETS_R.put("GameMessageS2CPacket", ClientboundSystemChatPacket.class);
      S2C_PACKETS.put(ClientboundSetPassengersPacket.class, "EntityPassengersSetS2CPacket");
      S2C_PACKETS_R.put("EntityPassengersSetS2CPacket", ClientboundSetPassengersPacket.class);
      S2C_PACKETS.put(ClientboundHelloPacket.class, "LoginHelloS2CPacket");
      S2C_PACKETS_R.put("LoginHelloS2CPacket", ClientboundHelloPacket.class);
      S2C_PACKETS.put(ClientboundLoginPacket.class, "GameJoinS2CPacket");
      S2C_PACKETS_R.put("GameJoinS2CPacket", ClientboundLoginPacket.class);
      S2C_PACKETS.put(ClientboundUpdateRecipesPacket.class, "SynchronizeRecipesS2CPacket");
      S2C_PACKETS_R.put("SynchronizeRecipesS2CPacket", ClientboundUpdateRecipesPacket.class);
      S2C_PACKETS.put(ClientboundMoveEntityPacket.class, "EntityS2CPacket");
      S2C_PACKETS_R.put("EntityS2CPacket", ClientboundMoveEntityPacket.class);
      S2C_PACKETS.put(ClientboundSetEntityDataPacket.class, "EntityTrackerUpdateS2CPacket");
      S2C_PACKETS_R.put("EntityTrackerUpdateS2CPacket", ClientboundSetEntityDataPacket.class);
      S2C_PACKETS.put(ClientboundEntityEventPacket.class, "EntityStatusS2CPacket");
      S2C_PACKETS_R.put("EntityStatusS2CPacket", ClientboundEntityEventPacket.class);
      S2C_PACKETS.put(ClientboundSelectKnownPacks.class, "SelectKnownPacksS2CPacket");
      S2C_PACKETS_R.put("SelectKnownPacksS2CPacket", ClientboundSelectKnownPacks.class);
      S2C_PACKETS.put(ClientboundRegistryDataPacket.class, "DynamicRegistriesS2CPacket");
      S2C_PACKETS_R.put("DynamicRegistriesS2CPacket", ClientboundRegistryDataPacket.class);
      S2C_PACKETS.put(ClientboundCustomPayloadPacket.class, "CustomPayloadS2CPacket");
      S2C_PACKETS_R.put("CustomPayloadS2CPacket", ClientboundCustomPayloadPacket.class);
      S2C_PACKETS.put(ClientboundSetSubtitleTextPacket.class, "SubtitleS2CPacket");
      S2C_PACKETS_R.put("SubtitleS2CPacket", ClientboundSetSubtitleTextPacket.class);
      S2C_PACKETS.put(ClientboundTagQueryPacket.class, "NbtQueryResponseS2CPacket");
      S2C_PACKETS_R.put("NbtQueryResponseS2CPacket", ClientboundTagQueryPacket.class);
      S2C_PACKETS.put(ClientboundSetEquipmentPacket.class, "EntityEquipmentUpdateS2CPacket");
      S2C_PACKETS_R.put("EntityEquipmentUpdateS2CPacket", ClientboundSetEquipmentPacket.class);
      S2C_PACKETS.put(ClientboundForgetLevelChunkPacket.class, "UnloadChunkS2CPacket");
      S2C_PACKETS_R.put("UnloadChunkS2CPacket", ClientboundForgetLevelChunkPacket.class);
      S2C_PACKETS.put(ClientboundFinishConfigurationPacket.class, "ReadyS2CPacket");
      S2C_PACKETS_R.put("ReadyS2CPacket", ClientboundFinishConfigurationPacket.class);
      S2C_PACKETS.put(ClientboundStartConfigurationPacket.class, "EnterReconfigurationS2CPacket");
      S2C_PACKETS_R.put("EnterReconfigurationS2CPacket", ClientboundStartConfigurationPacket.class);
      S2C_PACKETS.put(ClientboundSetEntityLinkPacket.class, "EntityAttachS2CPacket");
      S2C_PACKETS_R.put("EntityAttachS2CPacket", ClientboundSetEntityLinkPacket.class);
      S2C_PACKETS.put(ClientboundBossEventPacket.class, "BossBarS2CPacket");
      S2C_PACKETS_R.put("BossBarS2CPacket", ClientboundBossEventPacket.class);
      S2C_PACKETS.put(ClientboundTransferPacket.class, "ServerTransferS2CPacket");
      S2C_PACKETS_R.put("ServerTransferS2CPacket", ClientboundTransferPacket.class);
      S2C_PACKETS.put(ClientboundTeleportEntityPacket.class, "EntityPositionS2CPacket");
      S2C_PACKETS_R.put("EntityPositionS2CPacket", ClientboundTeleportEntityPacket.class);
      S2C_PACKETS.put(ClientboundLevelChunkWithLightPacket.class, "ChunkDataS2CPacket");
      S2C_PACKETS_R.put("ChunkDataS2CPacket", ClientboundLevelChunkWithLightPacket.class);
      S2C_PACKETS.put(ClientboundProjectilePowerPacket.class, "ProjectilePowerS2CPacket");
      S2C_PACKETS_R.put("ProjectilePowerS2CPacket", ClientboundProjectilePowerPacket.class);
      S2C_PACKETS.put(ClientboundGameEventPacket.class, "GameStateChangeS2CPacket");
      S2C_PACKETS_R.put("GameStateChangeS2CPacket", ClientboundGameEventPacket.class);
      S2C_PACKETS.put(ClientboundBundleDelimiterPacket.class, "BundleDelimiterS2CPacket");
      S2C_PACKETS_R.put("BundleDelimiterS2CPacket", ClientboundBundleDelimiterPacket.class);
      S2C_PACKETS.put(ClientboundBundlePacket.class, "BundleS2CPacket");
      S2C_PACKETS_R.put("BundleS2CPacket", ClientboundBundlePacket.class);
      S2C_PACKETS.put(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.PosRot.class, "EntityS2CPacket.RotateAndMoveRelative");
      S2C_PACKETS_R.put("EntityS2CPacket.RotateAndMoveRelative", net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.PosRot.class);
      S2C_PACKETS.put(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot.class, "EntityS2CPacket.Rotate");
      S2C_PACKETS_R.put("EntityS2CPacket.Rotate", net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot.class);
      S2C_PACKETS.put(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Pos.class, "EntityS2CPacket.MoveRelative");
      S2C_PACKETS_R.put("EntityS2CPacket.MoveRelative", net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Pos.class);
   }

   private static class PacketRegistry extends MappedRegistry<Class<? extends Packet<?>>> {
      public PacketRegistry() {
         super(ResourceKey.createRegistryKey(MeteorClient.identifier("packets")), Lifecycle.stable());
      }

      public int size() {
         return PacketUtils.S2C_PACKETS.keySet().size() + PacketUtils.C2S_PACKETS.keySet().size();
      }

      public ResourceLocation getKey(Class<? extends Packet<?>> entry) {
         return null;
      }

      public Optional<ResourceKey<Class<? extends Packet<?>>>> getResourceKey(Class<? extends Packet<?>> entry) {
         return Optional.empty();
      }

      public int getId(Class<? extends Packet<?>> entry) {
         return 0;
      }

      public Class<? extends Packet<?>> get(ResourceKey<Class<? extends Packet<?>>> key) {
         return null;
      }

      public Class<? extends Packet<?>> get(ResourceLocation id) {
         return null;
      }

      public Lifecycle registryLifecycle() {
         return null;
      }

      public Set<ResourceLocation> keySet() {
         return Collections.emptySet();
      }

      public boolean containsKey(ResourceLocation id) {
         return false;
      }

      public Class<? extends Packet<?>> get(int index) {
         return null;
      }

      @NotNull
      public Iterator<Class<? extends Packet<?>>> iterator() {
         return Stream.concat(PacketUtils.S2C_PACKETS.keySet().stream(), PacketUtils.C2S_PACKETS.keySet().stream()).iterator();
      }

      public boolean containsKey(ResourceKey<Class<? extends Packet<?>>> key) {
         return false;
      }

      public Set<Entry<ResourceKey<Class<? extends Packet<?>>>, Class<? extends Packet<?>>>> entrySet() {
         return Collections.emptySet();
      }

      public Optional<Reference<Class<? extends Packet<?>>>> getRandom(RandomSource random) {
         return Optional.empty();
      }

      public Registry<Class<? extends Packet<?>>> freeze() {
         return null;
      }

      public Reference<Class<? extends Packet<?>>> createEntry(Class<? extends Packet<?>> value) {
         return null;
      }

      public Optional<Reference<Class<? extends Packet<?>>>> getHolder(int rawId) {
         return Optional.empty();
      }

      public Optional<Reference<Class<? extends Packet<?>>>> getHolder(ResourceKey<Class<? extends Packet<?>>> key) {
         return Optional.empty();
      }

      public Stream<Reference<Class<? extends Packet<?>>>> holders() {
         return null;
      }

      public Optional<Named<Class<? extends Packet<?>>>> getTag(TagKey<Class<? extends Packet<?>>> tag) {
         return Optional.empty();
      }

      public Named<Class<? extends Packet<?>>> getOrCreateTag(TagKey<Class<? extends Packet<?>>> tag) {
         return null;
      }

      public Stream<Pair<TagKey<Class<? extends Packet<?>>>, Named<Class<? extends Packet<?>>>>> getTags() {
         return null;
      }

      public Stream<TagKey<Class<? extends Packet<?>>>> getTagNames() {
         return null;
      }

      public void resetTags() {
      }

      public void bindTags(Map<TagKey<Class<? extends Packet<?>>>, List<Holder<Class<? extends Packet<?>>>>> tagEntries) {
      }

      public Set<ResourceKey<Class<? extends Packet<?>>>> registryKeySet() {
         return Collections.emptySet();
      }
   }
}
