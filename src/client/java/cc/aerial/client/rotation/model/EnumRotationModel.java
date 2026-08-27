package cc.aerial.client.rotation.model;

import cc.aerial.client.rotation.RotationProperty;
import cc.aerial.client.rotation.model.impl.InstantRotationModel;
import cc.aerial.client.rotation.model.impl.LinearRotationModel;
import cc.aerial.client.rotation.model.impl.OrganicRotationModel;

import java.util.function.Function;

public enum EnumRotationModel {
    INSTANT("Instant", r -> InstantRotationModel.INSTANCE),
    LINEAR("Linear", r -> new LinearRotationModel(r.getMaxAngle())),
    ORGANIC("Organic", r -> new OrganicRotationModel(r.getMaxAngle(), r.getDriftIntensity(), r.getJitterIntensity()));

    private final String name;
    private final Function<RotationProperty, IRotationModel> modelSupplier;

    EnumRotationModel(String name, Function<RotationProperty, IRotationModel> modelSupplier) {
        this.name = name;
        this.modelSupplier = modelSupplier;
    }

    @Override
    public String toString() {
        return name;
    }

    public IRotationModel supply(RotationProperty property) {
        return modelSupplier.apply(property);
    }
}
