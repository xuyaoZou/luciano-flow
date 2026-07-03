package com.luciano.adapter.dto;

/**
 * 智能路由请求
 */
public class RouteRequest {

    /** 需要的能力标识 */
    private String capability;

    /** 路由偏好 */
    private RoutePreferenceDto preference;

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public RoutePreferenceDto getPreference() {
        return preference;
    }

    public void setPreference(RoutePreferenceDto preference) {
        this.preference = preference;
    }

    public static class RoutePreferenceDto {
        private String preferredAdapter;
        private String priority;  // QUALITY / SPEED / COST
        private Integer maxBudgetFen;

        public String getPreferredAdapter() { return preferredAdapter; }
        public void setPreferredAdapter(String preferredAdapter) { this.preferredAdapter = preferredAdapter; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public Integer getMaxBudgetFen() { return maxBudgetFen; }
        public void setMaxBudgetFen(Integer maxBudgetFen) { this.maxBudgetFen = maxBudgetFen; }
    }
}