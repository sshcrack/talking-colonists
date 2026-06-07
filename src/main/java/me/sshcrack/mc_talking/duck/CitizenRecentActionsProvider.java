package me.sshcrack.mc_talking.duck;

import java.util.List;

public interface CitizenRecentActionsProvider {
    void mc_talking$pushRecentAction(String entry);

    List<String> mc_talking$getRecentActions();
}
