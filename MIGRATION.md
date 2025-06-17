# Data Storage Migration
This version migrates from using Forge's data components to an NBT-based capability system for storing persistent data.

## Changes
- Replaced `ModAttachmentTypes` with the NBT capability system
- Added `EntityDataProvider` capability for storing entity-specific data
- Improved data persistence across game sessions and server restarts
- Better compatibility with other mods

## For Developers
If you were using the `ModAttachmentTypes.SESSION_TOKEN` attachment, you should now use the `EntityDataProvider` capability:

```java
// Old way (data components)
if (entity.hasData(ModAttachmentTypes.SESSION_TOKEN)) {
    String token = entity.getData(ModAttachmentTypes.SESSION_TOKEN);
    // Use token...
}

// New way (NBT capabilities)
EntityDataProvider.getFromEntity(entity).ifPresent(provider -> {
    String token = provider.getSessionToken();
    // Use token...
});

// Old way (setting data)
entity.setData(ModAttachmentTypes.SESSION_TOKEN, tokenValue);

// New way (setting data)
EntityDataProvider.getFromEntity(entity).ifPresent(provider -> {
    provider.setSessionToken(tokenValue);
});
```

The `ModAttachmentTypes` class has been kept for compatibility but is marked as deprecated and will be removed in a future version.
