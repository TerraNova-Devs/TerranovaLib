package de.mcterranova.terranovaLib.violetPDC;

import de.mcterranova.terranovaLib.violetPDC.DataTypes.InstantDataType;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;

public interface DataType {

    PersistentDataType<byte[], Instant> Instant = new InstantDataType();

}
