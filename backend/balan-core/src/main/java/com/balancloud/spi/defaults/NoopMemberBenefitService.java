package com.balancloud.spi.defaults;

import com.balancloud.spi.MemberBenefitService;
import com.balancloud.spi.dto.BenefitItem;
import com.balancloud.spi.dto.BenefitQuery;

import java.util.List;

public class NoopMemberBenefitService implements MemberBenefitService {

    @Override
    public List<BenefitItem> evaluate(BenefitQuery query) {
        return List.of();
    }
}
