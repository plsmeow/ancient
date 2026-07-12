package tech.onetap.util.neuro.rotation;

import ai.djl.ndarray.NDList;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

public class FloatArrayTranslator implements Translator<float[], float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, float[] input) {
        return new NDList(ctx.getNDManager().create(input).reshape(1, input.length));
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        return list.get(0).toFloatArray();
    }
}
