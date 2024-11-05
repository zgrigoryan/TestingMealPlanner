package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

import static org.example.DatabaseManager.DAYS_OF_WEEK;

public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static DatabaseManager db;

    private static Map<String, String> breakfastPlan = new LinkedHashMap<>();
    private static Map<String, String> lunchPlan = new LinkedHashMap<>();
    private static Map<String, String> dinnerPlan = new LinkedHashMap<>();

    private static Map<String, List<String>> mealsByCategory = new HashMap<>();

    public static void main(String[] args) {
        String DB_URL = "jdbc:postgresql://localhost:5432/meals_db";
        String USER = "postgres";
        String PASS = "1111";
            try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASS)) {
                db = new DatabaseManager(connection);
                db.initializeDatabase();
                String command = "";

                while (true) {
                    System.out.println("What would you like to do (add, show, plan, list plan, save, exit)?");
                    if (!scanner.hasNextLine()) {
                        // No more input; exit gracefully
                        break;
                    }
                    command = scanner.nextLine();

                    if (command.equals("exit")) {
                        System.out.println("Bye!");
                        break;
                    }

                    switch (command) {
                        case "add":
                            addMeal();
                            break;
                        case "show":
                            showMeals();
                            break;
                        case "plan":
                            planMeals();
                            break;
                        case "list plan":
                            listPlan();
                            break;
                        case "save":
                            save();
                            break;
                        default:
                            System.out.println("Unknown command");
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    private static void addMeal() {
        String name;
        String category;
        List<String> ingredients = new ArrayList<>();

        // get meal category
        while (true) {
            System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
            category = scanner.nextLine();
            if (isValidCategory(category)) {
                break;
            } else {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
            }
        }

        // get meal name
        while (true) {
            System.out.println("Input the meal's name:");
            name = scanner.nextLine();
            if (isValidName(name)) {
                break;
            } else {
                System.out.println("Wrong format. Use letters only!");
            }
        }

        // get the ingredients
        while (true) {
            System.out.println("Input the ingredients:");
            String ingredientsInput = scanner.nextLine();
            String[] ingredientsArray = ingredientsInput.split(",");
            boolean valid = true;
            ingredients.clear();

            for (String ingredient : ingredientsArray) {
                if (isValidIngredient(ingredient)) {
                    ingredients.add(ingredient);
                } else {
                    System.out.println("Wrong format. Use letters only!");
                    valid = false;
                    break;
                }
            }

            if (valid && !ingredients.isEmpty()) {
                break;
            }
        }

        try {
            db.addMeal(category, name, ingredients);
            System.out.println("The meal has been added!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void showMeals() {
        while (true) {
            System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
            String inputCategory = scanner.nextLine();

            if (!isValidCategory(inputCategory)) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
                continue;
            }

            try {
                List<Meal> mealList = db.getMealsByCategory(inputCategory);

                if (mealList.isEmpty()) {
                    System.out.println("No meals found.");
                    return;
                }
                System.out.println("Category: " + inputCategory);

                for (int i = 0; i < mealList.size(); i++) {
                    Meal meal = mealList.get(i);

                    if (i > 0) {
                        System.out.println();
                    }

                    System.out.println("Name: " + meal.getName());
                    System.out.println("Ingredients:");

                    for (String ingredient : meal.getIngredients()) {
                        System.out.println(ingredient);
                    }
                }

                break;

            } catch (SQLException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private static void planMeals() {
        mealsByCategory.clear();
        String[] categories = {"breakfast", "lunch", "dinner"};

        try {
            for (String category : categories) {
                List<Meal> meals = db.getMealsByCategoryAlphabetical(category);
                List<String> mealNames = new ArrayList<>();
                for (Meal meal : meals) {
                    mealNames.add(meal.getName());
                }
                mealsByCategory.put(category, mealNames);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // check if there are meals in each category
        if (mealsByCategory.get("breakfast").isEmpty() ||
                mealsByCategory.get("lunch").isEmpty() ||
                mealsByCategory.get("dinner").isEmpty()) {
            return;
        }

        // delete the old plan if it exists
        try {
            db.deleteOldPlan();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        for (String day : DAYS_OF_WEEK) {
            // plan breakfast, lunch, dinner
            System.out.println(day);
            planMealForCategory(day, "breakfast", breakfastPlan); //helper method
            planMealForCategory(day, "lunch", lunchPlan);
            planMealForCategory(day, "dinner", dinnerPlan);

            System.out.println("Yeah! We planned the meals for " + day + ".");
        }

        try {
            db.savePlanToDatabase(breakfastPlan, lunchPlan, dinnerPlan);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (String day : DAYS_OF_WEEK) {
            System.out.println(day);
            System.out.println("Breakfast: " + breakfastPlan.get(day));
            System.out.println("Lunch: " + lunchPlan.get(day));
            System.out.println("Dinner: " + dinnerPlan.get(day));
            System.out.println();
        }
    }

    private static void planMealForCategory(String day, String category, Map<String, String> mealPlan) {
        List<String> meals = mealsByCategory.get(category);

        for (String meal : meals) {
            System.out.println(meal);
        }

        System.out.println();
        String prompt = "Choose the " + category + " for " + day + " from the list above:";

        while (true) {
            System.out.println(prompt);
            String chosenMeal = scanner.nextLine();

            if (meals.contains(chosenMeal)) {
                mealPlan.put(day, chosenMeal);
                break;
            } else {
                System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
            }
        }
    }
    private static void listPlan() {
        try {
            Map<String, Map<String, String>> weeklyPlan = db.getPlannedMeals();

            if (weeklyPlan.isEmpty()) {
                System.out.println("No plan found. Please create a plan first.");
                return;
            }

            for (String day : DAYS_OF_WEEK) {
                Map<String, String> dayPlan = weeklyPlan.get(day);
                if (dayPlan != null) {
                    System.out.println(day);
                    System.out.println("Breakfast: " + dayPlan.get("breakfast"));
                    System.out.println("Lunch: " + dayPlan.get("lunch"));
                    System.out.println("Dinner: " + dayPlan.get("dinner"));
                    System.out.println();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try{
            Map<String, Map<String, String>> weeklyPlan = db.getPlannedMeals();

            if (weeklyPlan.isEmpty()) {
                System.out.println("Unable to save. Plan your meals first.");
                return;
            }

            Map<String, Integer> ingredientCounts = new HashMap<>();

            for (Map<String, String> dayPlan : weeklyPlan.values()) {
                for (String mealName : dayPlan.values()) {
                    int mealId = db.getMealId(mealName);
                    List<String> ingredients = db.getIngredientsForMeal(mealId);
                    for (String ingredient : ingredients) {
                        ingredientCounts.put(ingredient, ingredientCounts.getOrDefault(ingredient, 0) + 1);
                    }
                }
            }

            System.out.println("Input a filename:");
            String filename = scanner.nextLine();

            // write the shopping list to the file
            try (FileWriter writer = new FileWriter(filename)) {
                for (Map.Entry<String, Integer> entry : ingredientCounts.entrySet()) {
                    String ingredient = entry.getKey();
                    int count = entry.getValue();
                    if (count > 1) {
                        writer.write(ingredient + " x" + count + "\n");
                    } else {
                        writer.write(ingredient + "\n");
                    }
                }
                System.out.println("Saved!");
            } catch (IOException e) {
                System.out.println("Unable to save. Plan your meals first.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static boolean isValidName(String name) {
        return name.matches("[a-zA-Z ]+");
    }

    private static boolean isValidCategory(String category) {
        return category.equalsIgnoreCase("breakfast") || category.equalsIgnoreCase("lunch") || category.equalsIgnoreCase("dinner");
    }

    private static boolean isValidIngredient(String ingredient) {
        return ingredient.matches("[a-zA-Z ]+");
    }

    static class Meal {
        private final String category;
        private final String name;
        private final List<String> ingredients;

        public Meal(String category, String name, List<String> ingredients) {
            this.category = category;
            this.name = name;
            this.ingredients = ingredients;
        }

        public String getCategory() {
            return category;
        }

        public String getName() {
            return name;
        }

        public List<String> getIngredients() {
            return ingredients;
        }
    }
}
