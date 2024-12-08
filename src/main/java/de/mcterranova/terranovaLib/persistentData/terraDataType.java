package de.mcterranova.terranovaLib.persistentData;

import de.mcterranova.terranovaLib.persistentData.DataTypes.InstantDataType;
import de.mcterranova.terranovaLib.persistentData.DataTypes.UUIDDataType;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.util.UUID;

public interface terraDataType {

    PersistentDataType<byte[], Instant> Instant = new InstantDataType();
    PersistentDataType<byte[], UUID> UUID = new UUIDDataType();

}
