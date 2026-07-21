class DsaQ3 {
    //check the max water into the water tank and compare it .
    
    public int maxArea(int[] height) {
        int maxWater = 0;
        int left = 0;
        int right = height.length - 1;
        
        while (left < right) {
            int width = right - left;
            
            // Fixed: changed 'Height' to 'height'
            int currentHeight = Math.min(height[left], height[right]);
            
            int currentWater = width * currentHeight;
            
            // Fixed: changed 'maxwater' to 'maxWater'
            maxWater = Math.max(maxWater, currentWater);
            
            // Fixed: accurately matches the 'height' array parameter
            if (height[left] < height[right]) {
                left++;
            } else {
                right--;
            }
        }
        
        return maxWater;
    }
}