package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable player relation view to describe how the citizen should address/react to a player.
 */
public record PlayerRelationView(
        String playerName,
        String rankName,
        boolean hostile,
        boolean colonyLeadership
) {
}
