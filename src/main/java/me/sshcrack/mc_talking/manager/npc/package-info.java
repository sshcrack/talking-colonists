/**
 * Multi-NPC Conversation Framework for Gemini Live API.
 * 
 * <h2>Overview</h2>
 * This package provides a framework for simulating lively conversations between multiple
 * NPCs (Non-Player Characters), where each NPC has its own GeminiWsClient session for
 * independent AI-powered responses.
 * 
 * <h2>Architecture</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                    NPCConversationManager                          │
 * │  (Static manager for all NPC conversations)                        │
 * └─────────────────────────┬───────────────────────────────────────────┘
 *                           │
 *                           ▼
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                      NPCConversation                                │
 * │  (Individual conversation with multiple NPCs)                      │
 * │  - Manages message routing between NPCs                            │
 * │  - Uses NPCSpeakerScheduler to decide next speaker                 │
 * │  - Maintains conversation history                                  │
 * └─────────────────────────┬───────────────────────────────────────────┘
 *                           │
 *            ┌──────────────┼──────────────┐
 *            ▼              ▼              ▼
 * ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
 * │ NpcGeminiWs    │ │ NpcGeminiWs    │ │ NpcGeminiWs    │
 * │ Client (NPC1)  │ │ Client (NPC2)  │ │ Client (NPC3)  │
 * │                │ │                │ │                │
 * │ Uses compact   │ │ Uses compact   │ │ Uses compact   │
 * │ system prompt  │ │ system prompt  │ │ system prompt  │
 * │ from Generator │ │ from Generator │ │ from Generator │
 * └────────────────┘ └────────────────┘ └────────────────┘
 * </pre>
 * 
 * <h2>Key Components</h2>
 * <ul>
 *   <li>{@link me.sshcrack.mc_talking.manager.npc.NPCConversationManager} - 
 *       Main entry point for creating and managing conversations</li>
 *   <li>{@link me.sshcrack.mc_talking.manager.npc.NPCConversation} - 
 *       Represents a single conversation with multiple NPCs</li>
 *   <li>{@link me.sshcrack.mc_talking.manager.npc.NpcGeminiWsClient} - 
 *       Per-NPC Gemini WebSocket client</li>
 *   <li>{@link me.sshcrack.mc_talking.manager.npc.NPCSpeakerScheduler} - 
 *       Strategy for selecting the next speaker</li>
 *   <li>{@link me.sshcrack.mc_talking.manager.npc.NPCPromptGenerator} - 
 *       Generates compact system prompts per NPC</li>
 * </ul>
 * 
 * <h2>Conversation Flow</h2>
 * <ol>
 *   <li>Create a conversation via {@code NPCConversationManager.createConversation()}</li>
 *   <li>Add NPCs using {@code conversation.addNPC(citizenData)}</li>
 *   <li>Start the conversation with {@code NPCConversationManager.startConversation()}</li>
 *   <li>The scheduler picks the next speaker (server-side decision)</li>
 *   <li>NPC generates a response via their Gemini session</li>
 *   <li>Response is broadcast to other NPCs</li>
 *   <li>Repeat steps 4-6 until conversation ends</li>
 * </ol>
 * 
 * <h2>Speaker Selection (No next_speaker tool calls)</h2>
 * The server code decides which NPC speaks next using {@link me.sshcrack.mc_talking.manager.npc.NPCSpeakerScheduler}:
 * <ul>
 *   <li>ROUND_ROBIN - NPCs take turns in order</li>
 *   <li>RANDOM - Random selection (same NPC may speak consecutively)</li>
 *   <li>RANDOM_EXCLUDE_LAST - Random but prevents same NPC twice in a row</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Example 1: Creating and Starting a Conversation</h3>
 * <pre>{@code
 * // Create a conversation with round-robin scheduling
 * NPCConversation conversation = NPCConversationManager.createConversation(
 *     NPCSpeakerScheduler.ROUND_ROBIN
 * );
 * 
 * // Add NPCs (each gets their own Gemini session)
 * conversation.addNPC(farmerCitizen);
 * conversation.addNPC(minerCitizen);
 * conversation.addNPC(builderCitizen);
 * 
 * // Start with a topic
 * NPCConversationManager.startConversation(
 *     conversation.getConversationId(),
 *     "The harvest this year looks promising."
 * );
 * }</pre>
 * 
 * <h3>Example 2: Sending Message to Specific NPC</h3>
 * <pre>{@code
 * UUID targetNpcId = getNpcIdSomehow();
 * NPCConversationManager.sendToNPC(
 *     conversationId,
 *     targetNpcId,
 *     "Town Mayor",  // sender name
 *     "What's your opinion on the new mining regulations?"
 * );
 * }</pre>
 * 
 * <h3>Example 3: Broadcasting to All NPCs</h3>
 * <pre>{@code
 * NPCConversationManager.broadcastToAll(
 *     conversationId,
 *     "Town Bell",  // sender name
 *     "It's noon! Time for lunch break."
 * );
 * }</pre>
 * 
 * <h3>Example 4: Advancing Conversation (Manual Turn Taking)</h3>
 * <pre>{@code
 * // Trigger the next speaker based on scheduler
 * UUID nextSpeaker = NPCConversationManager.advanceConversation(
 *     conversationId,
 *     "Please share your thoughts."
 * );
 * }</pre>
 * 
 * <h2>System Prompt Structure</h2>
 * Each NPC's system prompt (generated by {@link me.sshcrack.mc_talking.manager.npc.NPCPromptGenerator}) includes:
 * <ul>
 *   <li>NPC Identity (name, gender, job, age)</li>
 *   <li>Key Personality Traits (derived from skills)</li>
 *   <li>Current Mood / Happiness level</li>
 *   <li>Short Backstory (relationships)</li>
 *   <li>Memory / Current Events</li>
 *   <li>List of Other NPCs in Conversation</li>
 *   <li>Rules: speak only as self, short messages, never simulate others</li>
 * </ul>
 * 
 * @see me.sshcrack.mc_talking.manager.npc.NPCConversationManager
 * @see me.sshcrack.mc_talking.manager.npc.NPCConversation
 * @see me.sshcrack.mc_talking.manager.npc.NpcGeminiWsClient
 */
package me.sshcrack.mc_talking.manager.npc;
