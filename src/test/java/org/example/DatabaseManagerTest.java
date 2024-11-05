package org.example;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class DatabaseManagerTest {

    private Connection mockConnection;
    private Statement mockStatement;
    private PreparedStatement mockPreparedStatement;
    private ResultSet mockResultSet;
    private DatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws SQLException {
        mockConnection = mock(Connection.class);
        mockStatement = mock(Statement.class);
        mockPreparedStatement = mock(PreparedStatement.class);
        mockResultSet = mock(ResultSet.class);
        databaseManager = new DatabaseManager(mockConnection);
    }

    @Test
    @DisplayName("Should initialize database tables")
    void initializeDatabase() throws SQLException {
        given(mockConnection.createStatement()).willReturn(mockStatement);

        String expectedMealsTableQuery = "CREATE TABLE IF NOT EXISTS meals (" +
                "category VARCHAR(1024) NOT NULL," +
                "meal VARCHAR(1024) NOT NULL," +
                "meal_id INTEGER NOT NULL" +
                ")";
        String expectedIngredientsTableQuery = "CREATE TABLE IF NOT EXISTS ingredients (" +
                "ingredient VARCHAR(1024) NOT NULL," +
                "ingredient_id INTEGER NOT NULL," +
                "meal_id INTEGER NOT NULL" +
                ")";
        String expectedPlanTableQuery = "CREATE TABLE IF NOT EXISTS plan (" +
                "day VARCHAR(1024) NOT NULL," +
                "meal_category VARCHAR(1024) NOT NULL," +
                "meal_id INTEGER NOT NULL," +
                "meal_option VARCHAR(1024) NOT NULL" +
                ")";

        // When
        databaseManager.initializeDatabase();

        // Then
        then(mockStatement).should(times(1)).executeUpdate(expectedMealsTableQuery);
        then(mockStatement).should(times(1)).executeUpdate(expectedIngredientsTableQuery);
        then(mockStatement).should(times(1)).executeUpdate(expectedPlanTableQuery);
        then(mockStatement).should(times(1)).close();
    }


    @Test
    @DisplayName("Should add a meal with ingredients")
    void addMeal() throws SQLException {
        // Given
        String category = "breakfast";
        String name = "Pancakes";
        List<String> ingredients = Arrays.asList("Flour", "Eggs", "Milk");

        int nextMealId = 1;
        // Spy on databaseManager to mock getNextMealId and getNextIngredientId
        DatabaseManager spyDatabaseManager = spy(databaseManager);
        doReturn(nextMealId).when(spyDatabaseManager).getNextMealId();
        doReturn(1, 2, 3).when(spyDatabaseManager).getNextIngredientId();

        PreparedStatement mockMealStmt = mock(PreparedStatement.class);
        PreparedStatement mockIngredientStmt = mock(PreparedStatement.class);

        given(mockConnection.prepareStatement("INSERT INTO meals (category, meal, meal_id) VALUES (?, ?, ?)")).willReturn(mockMealStmt);
        given(mockConnection.prepareStatement("INSERT INTO ingredients (ingredient, ingredient_id, meal_id) VALUES (?, ?, ?)")).willReturn(mockIngredientStmt);

        // When
        spyDatabaseManager.addMeal(category, name, ingredients);

        // Then
        then(mockMealStmt).should().setString(1, category);
        then(mockMealStmt).should().setString(2, name);
        then(mockMealStmt).should().setInt(3, nextMealId);
        then(mockMealStmt).should().executeUpdate();
        then(mockMealStmt).should().close();

        ArgumentCaptor<String> ingredientCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> ingredientIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> mealIdCaptor = ArgumentCaptor.forClass(Integer.class);

        then(mockIngredientStmt).should(times(ingredients.size())).setString(eq(1), ingredientCaptor.capture());
        then(mockIngredientStmt).should(times(ingredients.size())).setInt(eq(2), ingredientIdCaptor.capture());
        then(mockIngredientStmt).should(times(ingredients.size())).setInt(eq(3), mealIdCaptor.capture());
        then(mockIngredientStmt).should(times(ingredients.size())).executeUpdate();
        then(mockIngredientStmt).should(times(ingredients.size())).close();

        assertEquals(ingredients, ingredientCaptor.getAllValues());
        assertEquals(Arrays.asList(1, 2, 3), ingredientIdCaptor.getAllValues());
        mealIdCaptor.getAllValues().forEach(id -> assertEquals(nextMealId, id));
    }

    @Test
    @DisplayName("Should retrieve meals by category")
    void getMealsByCategory() throws SQLException {
        // Given
        String category = "breakfast";
        PreparedStatement mockStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        given(mockConnection.prepareStatement("SELECT * FROM meals WHERE LOWER(category) = ? ORDER BY meal_id")).willReturn(mockStatement);
        given(mockStatement.executeQuery()).willReturn(mockResultSet);

        // Simulate result set
        given(mockResultSet.next()).willReturn(true, false);
        given(mockResultSet.getInt("meal_id")).willReturn(1);
        given(mockResultSet.getString("meal")).willReturn("Pancakes");

        PreparedStatement mockIngredientStmt = mock(PreparedStatement.class);
        ResultSet mockIngredientRs = mock(ResultSet.class);

        given(mockConnection.prepareStatement("SELECT ingredient FROM ingredients WHERE meal_id = ?")).willReturn(mockIngredientStmt);
        given(mockIngredientStmt.executeQuery()).willReturn(mockIngredientRs);
        given(mockIngredientRs.next()).willReturn(true, true, true, false);
        given(mockIngredientRs.getString("ingredient")).willReturn("Flour", "Eggs", "Milk");

        // When
        List<Main.Meal> meals = databaseManager.getMealsByCategory(category);

        // Then
        assertNotNull(meals);
        assertEquals(1, meals.size());
        Main.Meal meal = meals.get(0);
        assertEquals("Pancakes", meal.getName());
        assertEquals(Arrays.asList("Flour", "Eggs", "Milk"), meal.getIngredients());

        then(mockStatement).should().setString(1, category);
        then(mockStatement).should().executeQuery();
        then(mockStatement).should().close();

        then(mockIngredientStmt).should().setInt(1, 1);
        then(mockIngredientStmt).should().executeQuery();
        then(mockIngredientStmt).should().close();
    }

    @Test
    @DisplayName("Should delete old plan")
    void deleteOldPlan() throws SQLException {
        // Given
        Statement mockStatement = mock(Statement.class);
        given(mockConnection.createStatement()).willReturn(mockStatement);

        // When
        databaseManager.deleteOldPlan();

        // Then
        then(mockStatement).should().executeUpdate("DELETE FROM plan");
        then(mockStatement).should().close();
    }

    @Test
    @DisplayName("Should save plan to database")
    void savePlanToDatabase() throws SQLException {
        // Given
        Map<String, String> breakfastPlan = new HashMap<>();
        Map<String, String> lunchPlan = new HashMap<>();
        Map<String, String> dinnerPlan = new HashMap<>();

        for (String day : DatabaseManager.DAYS_OF_WEEK) {
            breakfastPlan.put(day, "Pancakes");
            lunchPlan.put(day, "Sandwich");
            dinnerPlan.put(day, "Pasta");
        }

        PreparedStatement mockPlanStmt = mock(PreparedStatement.class);
        given(mockConnection.prepareStatement(anyString())).willReturn(mockPlanStmt);

        DatabaseManager spyDatabaseManager = spy(databaseManager);
        doReturn(1).when(spyDatabaseManager).getMealId(anyString());

        // When
        spyDatabaseManager.savePlanToDatabase(breakfastPlan, lunchPlan, dinnerPlan);

        // Then
        int totalSetStringCalls = 7 * 3 * 3; // 63
        int totalSetIntCalls = 7 * 3 * 1;    // 21

        verify(mockPlanStmt, times(totalSetStringCalls)).setString(anyInt(), anyString());
        verify(mockPlanStmt, times(totalSetIntCalls)).setInt(anyInt(), anyInt());
        verify(mockPlanStmt, times(1)).executeBatch();
        verify(mockPlanStmt, times(1)).close();
    }



    @Test
    @DisplayName("Should retrieve planned meals")
    void getPlannedMeals() throws SQLException {
        // Given
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        ResultSet mockRs = mock(ResultSet.class);

        given(mockConnection.prepareStatement("SELECT * FROM plan")).willReturn(mockStmt);
        given(mockStmt.executeQuery()).willReturn(mockRs);

        given(mockRs.next()).willReturn(true, false);
        given(mockRs.getString("day")).willReturn("Monday");
        given(mockRs.getString("meal_category")).willReturn("breakfast");
        given(mockRs.getString("meal_option")).willReturn("Pancakes");

        // When
        Map<String, Map<String, String>> plannedMeals = databaseManager.getPlannedMeals();

        // Then
        assertNotNull(plannedMeals);
        assertTrue(plannedMeals.containsKey("Monday"));
        Map<String, String> mondayMeals = plannedMeals.get("Monday");
        assertEquals("Pancakes", mondayMeals.get("breakfast"));
    }

    @Test
    @DisplayName("Should get meal ID by meal name")
    void getMealId() throws SQLException {
        // Given
        String mealName = "Pancakes";
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        ResultSet mockRs = mock(ResultSet.class);

        given(mockConnection.prepareStatement("SELECT meal_id FROM meals WHERE meal = ?")).willReturn(mockStmt);
        given(mockStmt.executeQuery()).willReturn(mockRs);
        given(mockRs.next()).willReturn(true);
        given(mockRs.getInt("meal_id")).willReturn(1);

        // When
        int mealId = databaseManager.getMealId(mealName);

        // Then
        assertEquals(1, mealId);
        then(mockStmt).should().setString(1, mealName);
        then(mockStmt).should().executeQuery();
        then(mockStmt).should().close();
    }

    @Test
    @DisplayName("Should get next meal ID")
    void getNextMealId() throws SQLException {
        // Given
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConnection.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("SELECT MAX(meal_id) FROM meals")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getObject(1)).thenReturn(5); // Ensure getObject(1) returns non-null
        when(mockRs.getInt(1)).thenReturn(5);    // Set maxId to 5

        // When
        int nextMealId = databaseManager.getNextMealId();

        // Then
        assertEquals(6, nextMealId);
        verify(mockStmt).close();
    }



    @Test
    @DisplayName("Should get next ingredient ID")
    void getNextIngredientId() throws SQLException {
        // Given
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockConnection.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("SELECT MAX(ingredient_id) FROM ingredients")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getObject(1)).thenReturn(10); // Ensure getObject(1) returns non-null
        when(mockRs.getInt(1)).thenReturn(10);    // Set maxId to 10

        // When
        int nextIngredientId = databaseManager.getNextIngredientId();

        // Then
        assertEquals(11, nextIngredientId);
        verify(mockStmt).close();
    }



    @Test
    @DisplayName("Should get ingredients for a meal")
    void getIngredientsForMeal() throws SQLException {
        // Given
        int mealId = 1;
        PreparedStatement mockIngredientStmt = mock(PreparedStatement.class);
        ResultSet mockIngredientRs = mock(ResultSet.class);

        given(mockConnection.prepareStatement("SELECT ingredient FROM ingredients WHERE meal_id = ?")).willReturn(mockIngredientStmt);
        given(mockIngredientStmt.executeQuery()).willReturn(mockIngredientRs);
        given(mockIngredientRs.next()).willReturn(true, true, true, false);
        given(mockIngredientRs.getString("ingredient")).willReturn("Flour", "Eggs", "Milk");

        // When
        List<String> ingredients = databaseManager.getIngredientsForMeal(mealId);

        // Then
        assertEquals(Arrays.asList("Flour", "Eggs", "Milk"), ingredients);

        then(mockIngredientStmt).should().setInt(1, mealId);
        then(mockIngredientStmt).should().executeQuery();
        then(mockIngredientStmt).should().close();
    }
}
