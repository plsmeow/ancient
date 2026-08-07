package tech.onetap.util.neuro.rotation;

import lombok.Getter;

import java.io.Closeable;

/**
 * Иммутабельный holder активной модели.
 *
 * Собирается целиком до публикации в AtomicReference, поэтому игровой поток
 * никогда не видит полусобранную модель — это и есть атомарный swap из §4.
 */
@Getter
public class ActiveModel implements Closeable {

    private final String name;
    private final NeuroModelMeta meta;
    private final FeatureNormalizer normalizer;
    private final InferenceEngine engine;

    public ActiveModel(String name, NeuroModelMeta meta, FeatureNormalizer normalizer, InferenceEngine engine) {
        this.name = name;
        this.meta = meta;
        this.normalizer = normalizer;
        this.engine = engine;
    }

    @Override
    public void close() {
        engine.close();
    }
}
