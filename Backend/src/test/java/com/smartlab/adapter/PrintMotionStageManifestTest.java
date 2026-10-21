package com.smartlab.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class PrintMotionStageManifestTest {

    @Test
    void printParsedConfig() throws Exception {
        String ini = Files.readString(Path.of("D:/SmartLab2.0/adapter/new/MotionStageController/adapterSetup.ini"));
        ObjectNode manifest = new AdapterManifestService()
                .parseRawConfig("MotionStageAdapter", "INI", ini, 1792479820875L);
        Files.writeString(Path.of("D:/SmartLab2.0/adapter/new/MotionStageController/_parsed_config.json"),
                manifest.toPrettyString());
    }
}
