package com.balancloud.framework.sensitive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void masksPhoneKeepingPrefixAndTail() {
        assertThat(SensitiveDataMasker.maskPhone("13800000001")).isEqualTo("138****0001");
        assertThat(SensitiveDataMasker.maskPhone(" 13912345678 ")).isEqualTo("139****5678");
        assertThat(SensitiveDataMasker.maskPhone("")).isEmpty();
        assertThat(SensitiveDataMasker.maskPhone(null)).isNull();
    }

    @Test
    void masksShortPhoneWithoutLeakingTail() {
        assertThat(SensitiveDataMasker.maskPhone("1234")).isEqualTo("1***4");
        assertThat(SensitiveDataMasker.maskPhone("123")).isEqualTo("***");
    }

    @Test
    void masksPlateKeepingProvinceAndTail() {
        assertThat(SensitiveDataMasker.maskPlate("粤B12345")).isEqualTo("粤B***45");
        assertThat(SensitiveDataMasker.maskPlate("粤BD12345")).isEqualTo("粤B***45");
        assertThat(SensitiveDataMasker.maskPlate("B1234")).isEqualTo("B1***34");
        assertThat(SensitiveDataMasker.maskPlate(null)).isNull();
    }
}
