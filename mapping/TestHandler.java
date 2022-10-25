package com.godmao.func.mapping;

import org.springframework.stereotype.Component;

@Component
public class TestHandler extends FuncHandler<FuncController, FuncMapping> {


    @Override
    SFunction<FuncMapping, String[]> setMappikey() {
        return FuncMapping::value;
    }
    

}
