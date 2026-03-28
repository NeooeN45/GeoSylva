plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("dem_tiles")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
