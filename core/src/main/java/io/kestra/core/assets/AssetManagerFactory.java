package io.kestra.core.assets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.kestra.core.runners.AssetEmit;
import io.kestra.core.runners.AssetEmitter;

import jakarta.inject.Singleton;

@Singleton
public class AssetManagerFactory {
    /**
     * @param enabled whether the task's {@code assets.enableAuto} lets plugins register the assets they touch; when it does not, emits are dropped.
     */
    public AssetEmitter of(boolean enabled) {
        List<AssetEmit> emitted = Collections.synchronizedList(new ArrayList<>());
        return new AssetEmitter() {
            @Override
            public void emit(AssetEmit assetEmit) {
                if (enabled) {
                    emitted.add(assetEmit);
                }
            }

            @Override
            public List<AssetEmit> emitted() {
                return List.copyOf(emitted);
            }
        };
    }
}
