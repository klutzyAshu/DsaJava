import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
//Two sum question.

class DsaQ1 {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (seen.containsKey(complement)) {
                return new int[] { seen.get(complement), i };
            }
            seen.put(nums[i], i);
        }
        return new int[] {};
    }

    public static void main(String[] args) {
        DsaQ1 solver = new DsaQ1();
        int[] testNums = { 2, 7, 11, 15 };
        int testTarget = 9;
        int[] result = solver.twoSum(testNums, testTarget);
        System.out.println("Indices: " + Arrays.toString(result));
    }
}
