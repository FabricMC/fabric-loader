package net.fabricmc.oldGuavaFix;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;

import net.minecraft.class_1085;
import net.minecraft.class_1088;
import net.minecraft.class_1293;
import net.minecraft.class_160;
import net.minecraft.class_376;

public class GuavaMixin {
	
    private Map<class_160, class_1088> field_4653;
    
    private Set<class_160> field_4654;
    
    public void method_3731(class_160 class_160, class_1088 class_1088) {
        this.field_4653.put(class_160, class_1088);
    }
    
    public void method_3732(class_160... allRightsReserved) {
        Collections.<class_160>addAll(this.field_4654, allRightsReserved);
    }
	
	public Map<class_376, class_1293> method_3730() {
        Map<class_376, class_1293> map2 = Maps.newIdentityHashMap();
        for (final class_160 lv : class_160.field_593) {
            if (this.field_4654.contains(lv)) {
                continue;
            }
            map2.putAll(((class_1088)MoreObjects.firstNonNull(this.field_4653.get(lv), new class_1085())).method_3737(lv));
        }
        return map2;
	}
	
}
