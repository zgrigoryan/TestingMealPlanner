package org.example;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private Connection connection;

    private static final String CREATE_MEALS_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS meals (" +
            "category VARCHAR(1024) NOT NULL," +
            "meal VARCHAR(1024) NOT NULL," +
            "meal_id INTEGER NOT NULL" +
            ")";
    private static final String CREATE_INGREDIENTS_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS ingredients (" +
            "ingredient VARCHAR(1024) NOT NULL," +
            "ingredient_id INTEGER NOT NULL," +
            "meal_id INTEGER NOT NULL" +
            ")";
    private static final String CREATE_PLAN_TABLE_QUERY = "CREATE TABLE IF NOT EXISTS plan (" +
            "day VARCHAR(1024) NOT NULL," +
            "meal_category VARCHAR(1024) NOT NULL," +
            "meal_id INTEGER NOT NULL," +
            "meal_option VARCHAR(1024) NOT NULL" +
            ")";

    public static final String[] DAYS_OF_WEEK = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    public DatabaseManager(Connection connection) {
        this.connection = connection;
    }

    public void initializeDatabase() throws SQLException {
        Statement statement = connection.createStatement();
        statement.executeUpdate(CREATE_MEALS_TABLE_QUERY);
        statement.executeUpdate(CREATE_INGREDIENTS_TABLE_QUERY);
        statement.executeUpdate(CREATE_PLAN_TABLE_QUERY);
        statement.close();
    }


    public void addMeal(String category, String name, List<String> ingredients) throws SQLException {
        int mealId = getNextMealId();

        String insertMealQuery = "INSERT INTO meals (category, meal, meal_id) VALUES (?, ?, ?)";
        PreparedStatement mealStmt = connection.prepareStatement(insertMealQuery);
        mealStmt.setString(1, category);
        mealStmt.setString(2, name);
        mealStmt.setInt(3, mealId);
        mealStmt.executeUpdate();

        for (String ingredient : ingredients) {
            int ingredientId = getNextIngredientId();

            String insertIngredientQuery = "INSERT INTO ingredients (ingredient, ingredient_id, meal_id) VALUES (?, ?, ?)";
            PreparedStatement ingredientStmt = connection.prepareStatement(insertIngredientQuery);
            ingredientStmt.setString(1, ingredient);
            ingredientStmt.setInt(2, ingredientId);
            ingredientStmt.setInt(3, mealId);
            ingredientStmt.executeUpdate();
            ingredientStmt.close();
        }

        mealStmt.close();
    }

    // get meals in the order they were added
    public List<Main.Meal> getMealsByCategory(String category) throws SQLException {
        String query = "SELECT * FROM meals WHERE LOWER(category) = ? ORDER BY meal_id";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, category);

        ResultSet rs = statement.executeQuery();

        List<Main.Meal> mealList = new ArrayList<>();

        while (rs.next()) {
            int id = rs.getInt("meal_id");
            String mealName = rs.getString("meal");

            PreparedStatement ps = connection.prepareStatement(
                    "SELECT ingredient FROM ingredients WHERE meal_id = ?"
            );
            ps.setInt(1, id);
            ResultSet ingredientRs = ps.executeQuery();

            List<String> ingredients = new ArrayList<>();
            while (ingredientRs.next()) {
                ingredients.add(ingredientRs.getString("ingredient"));
            }

            ps.close();
            ingredientRs.close();

            Main.Meal meal = new Main.Meal(category, mealName, ingredients);
            mealList.add(meal);
        }

        rs.close();
        statement.close();

        return mealList;
    }

    // get meals in their alphabetical order
    public List<Main.Meal> getMealsByCategoryAlphabetical(String category) throws SQLException {
        String query = "SELECT * FROM meals WHERE LOWER(category) = ? ORDER BY meal";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, category);

        ResultSet rs = statement.executeQuery();

        List<Main.Meal> mealList = new ArrayList<>();

        while (rs.next()) {
            int id = rs.getInt("meal_id");
            String mealName = rs.getString("meal");

            PreparedStatement ps = connection.prepareStatement(
                    "SELECT ingredient FROM ingredients WHERE meal_id = ?"
            );
            ps.setInt(1, id);
            ResultSet ingredientRs = ps.executeQuery();

            List<String> ingredients = new ArrayList<>();
            while (ingredientRs.next()) {
                ingredients.add(ingredientRs.getString("ingredient"));
            }

            ps.close();
            ingredientRs.close();

            Main.Meal meal = new Main.Meal(category, mealName, ingredients);
            mealList.add(meal);
        }

        rs.close();
        statement.close();

        return mealList;
    }

    public void deleteOldPlan() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("DELETE FROM plan");
        stmt.close();
    }

    public void savePlanToDatabase(Map<String, String> breakfastPlan, Map<String, String> lunchPlan, Map<String, String> dinnerPlan) throws SQLException {
        String insertPlanQuery = "INSERT INTO plan (day, meal_category, meal_id, meal_option) VALUES (?, ?, ?, ?)";
        PreparedStatement planStmt = connection.prepareStatement(insertPlanQuery);

        for (String day : DAYS_OF_WEEK) {
            // Breakfast
            String breakfast = breakfastPlan.get(day);
            int breakfastId = getMealId(breakfast);
            planStmt.setString(1, day);
            planStmt.setString(2, "breakfast");
            planStmt.setInt(3, breakfastId);
            planStmt.setString(4, breakfast);
            planStmt.addBatch();

            // Lunch
            String lunch = lunchPlan.get(day);
            int lunchId = getMealId(lunch);
            planStmt.setString(1, day);
            planStmt.setString(2, "lunch");
            planStmt.setInt(3, lunchId);
            planStmt.setString(4, lunch);
            planStmt.addBatch();

            // Dinner
            String dinner = dinnerPlan.get(day);
            int dinnerId = getMealId(dinner);
            planStmt.setString(1, day);
            planStmt.setString(2, "dinner");
            planStmt.setInt(3, dinnerId);
            planStmt.setString(4, dinner);
            planStmt.addBatch();
        }

        planStmt.executeBatch();
        planStmt.close();
    }

    public Map<String, Map<String, String>> getPlannedMeals() throws SQLException {
        String query = "SELECT * FROM plan";
        PreparedStatement stmt = connection.prepareStatement(query);
        ResultSet rs = stmt.executeQuery();

        Map<String, Map<String, String>> weeklyPlan = new LinkedHashMap<>();
        while (rs.next()) {
            String day = rs.getString("day");
            String category = rs.getString("meal_category");
            String mealOptionStr = rs.getString("meal_option");

            weeklyPlan.computeIfAbsent(day, k -> new HashMap<>()).put(category, mealOptionStr);
        }

        rs.close();
        stmt.close();

        return weeklyPlan;
    }


    public int getMealId(String mealName) throws SQLException {
        String query = "SELECT meal_id FROM meals WHERE meal = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, mealName);
        ResultSet rs = stmt.executeQuery();
        int mealId = -1;
        if (rs.next()) {
            mealId = rs.getInt("meal_id");
        }
        rs.close();
        stmt.close();
        return mealId;
    }

    public int getNextMealId() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT MAX(meal_id) FROM meals");
        int maxId = 0;
        if (rs.next() && rs.getObject(1) != null) {
            maxId = rs.getInt(1);
        }
        stmt.close();
        return maxId + 1;
    }


    public int getNextIngredientId() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT MAX(ingredient_id) FROM ingredients");
        int maxId = 0;
        if (rs.next() && rs.getObject(1) != null) {
            maxId = rs.getInt(1);
        }
        stmt.close();
        return maxId + 1;
    }

    public List<String> getIngredientsForMeal(int mealId) throws SQLException {
        String ingredientQuery = "SELECT ingredient FROM ingredients WHERE meal_id = ?";
        PreparedStatement ingredientStmt = connection.prepareStatement(ingredientQuery);
        ingredientStmt.setInt(1, mealId);
        ResultSet ingredientRs = ingredientStmt.executeQuery();

        List<String> ingredients = new ArrayList<>();
        while (ingredientRs.next()) {
            ingredients.add(ingredientRs.getString("ingredient"));
        }

        ingredientRs.close();
        ingredientStmt.close();

        return ingredients;
    }
}
