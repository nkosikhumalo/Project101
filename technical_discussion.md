# Technical Discussion

## 1. Why PostgreSQL Instead of Supabase or SQLite

We used PostgreSQL running locally, accessed through a Java REST API. Here is why that made more sense than the other two options for this project.

### Why not SQLite?

SQLite is a file-based database that runs inside the app itself. It is fine for simple, single-device apps where only one user reads and writes data at a time. The problem is that our app has two separate parts — the Android app and the Java backend — and they run in different environments. The backend is on the host machine, and the app is inside an Android emulator. SQLite cannot be shared across a network connection, so it would only work if the database lived on the Android device itself. That would mean no central server, no REST API, and no way to add multi-device support later.

### Why not Supabase?

Supabase is a hosted service that wraps PostgreSQL and gives you a REST API automatically. It sounds convenient, but it has a few problems for this project. First, it requires an internet connection and a Supabase account, which adds an external dependency we do not control. If the Supabase service is down or the free tier has rate limits, the app breaks. Second, our brief asked us to build a backend ourselves, so using Supabase would skip the part of the work that demonstrates how the API layer functions. Third, running everything locally means no latency from an external server and no risk of network issues during a demo or assessment.

### Why PostgreSQL?

PostgreSQL runs on the same machine as the backend. The Java API connects to it using a JDBC URL (`jdbc:postgresql://127.0.0.1:5432/smartpantry` — `Server.java` line 22), which means the database is always available as long as the local server is running. PostgreSQL also enforces proper constraints — for example, the `CHECK(quantity > 0)` rule in `schema.sql` line 2 means invalid data cannot be inserted even if someone bypasses the API. It supports `JSONB` for storing recipe ingredients as a structured array inside a single column, and it handles concurrent requests safely, which matters because `Server.java` runs eight threads in parallel (line 40: `Executors.newFixedThreadPool(8)`).

In short: SQLite cannot be shared over a network, Supabase adds an external dependency we do not control, and PostgreSQL gives us full control, proper constraints, and runs entirely locally.

---

## 2. How the Activity Lifecycle Works in This App

Android activities are not always running. The system starts them, pauses them, and destroys them as the user moves around the app. Understanding this is important because it affects when data loads and when memory gets freed.

Every activity in this app extends [`BaseActivity`](app/src/main/java/za/co/smartpantry/BaseActivity.java), which extends Android's `Activity` class. The two lifecycle methods we use are `onCreate` and `onResume`.

### onCreate

`onCreate` runs once when the screen is first created. This is where we build the layout and set up the adapter. In [`PantryActivity.java`](app/src/main/java/za/co/smartpantry/PantryActivity.java) at line 17, `onCreate` calls `startScreen("My Pantry")`, which builds the title bar, scroll area, and navigation buttons. It also creates the `ListView` and attaches the `PantryAdapter`. We do not load data here on purpose — the list might be stale by the time the user returns to this screen.

### onResume

`onResume` runs every time the screen becomes visible — not just the first time, but also when the user navigates back from another screen. In [`PantryActivity.java`](app/src/main/java/za/co/smartpantry/PantryActivity.java) at line 42:

```java
@Override
protected void onResume() {
    super.onResume();
    if (adapter != null) {
        loadPantry();
    }
}
```

This means the pantry list always refreshes when you come back from adding or editing an ingredient. Without this, you would add an item, press back, and still see the old list.

### Why this matters for IngredientFormActivity

When the user taps "Edit" on a pantry item, [`PantryActivity`](app/src/main/java/za/co/smartpantry/PantryActivity.java) creates an `Intent`, puts the ingredient's data into it as extras, and calls `startActivity`. Android creates [`IngredientFormActivity`](app/src/main/java/za/co/smartpantry/IngredientFormActivity.java) on top of the back stack. When the user saves or presses back, that activity calls `finish()`, which destroys it and brings `PantryActivity` back to the foreground. At that point `onResume` fires again, `loadPantry()` runs, and the updated list appears. The lifecycle is what makes this work automatically without any extra signalling between activities.

---

## 3. How the ListView and Adapter Display Data

Android does not let you just hand a list of objects to a `ListView` and have it draw them. You need an adapter — a class that sits between the data and the view and tells the `ListView` how to draw each row.

### The adapter contract

`PantryAdapter` extends `BaseAdapter` and must answer three questions the `ListView` asks:

- `getCount()` — how many rows are there? (`PantryAdapter.java` line 31)
- `getItem(position)` — what object is at this position? (line 32)
- `getView(position, recycledView, parent)` — what does the row look like? (line 35)

`getView` is where the actual drawing happens. The `ListView` calls it for every visible row, passing in a `recycledView` — a row that has scrolled off screen and can be reused. The adapter checks whether the recycled view is the right type and reuses it if so, which avoids creating new layout objects for every row and keeps scrolling smooth:

```java
if (recycledView instanceof LinearLayout) {
    row       = (LinearLayout) recycledView;
    label     = (TextView) row.getChildAt(0);
    editBtn   = (TextView) row.getChildAt(1);
    deleteBtn = (TextView) row.getChildAt(2);
} else {
    // build a new row from scratch
}
```

(`PantryAdapter.java` lines 38–65)

### Connecting data to the view

Once the row exists, the adapter sets its content from the current `Ingredient` object:

```java
Ingredient item = items.get(position);
label.setText(item.label());
editBtn.setOnClickListener(v -> listener.onEdit(item));
deleteBtn.setOnClickListener(v -> listener.onDelete(item));
```

(`PantryAdapter.java` lines 67–70)

### Refreshing after a change

The `items` list is shared between `PantryActivity` and `PantryAdapter`. When `loadPantry()` finishes in `PantryActivity`, it clears the list, fills it with fresh data from the API, then calls `adapter.refresh()` (line 62), which calls `notifyDataSetChanged()`. This tells the `ListView` to re-ask `getCount()` and `getView()` for every visible row, so the screen updates without recreating anything.

---

## 4. How the Strict-Matching Algorithm Decides Whether a Recipe Is Suggested

The rule is simple: a recipe only appears in the suggestions list if every single ingredient it needs is available in the pantry in at least the required quantity. One missing ingredient means the whole recipe is excluded.

The logic lives in two methods in `Server.java`: `suggested()` and `hasAllIngredients()`.

### Step 1 — Load the pantry and all recipes

`suggested()` (line 248) starts by reading every row from `pantry_items` into a `JSONArray`. It then reads every recipe. For each recipe it calls `hasAllIngredients`. If that returns `true`, the recipe goes into the result list.

```java
for (int i = 0; i < recipes.length(); i++) {
    JSONObject recipe = recipes.getJSONObject(i);
    if (hasAllIngredients(recipe, pantry)) {
        matches.put(recipe);
    }
}
```

### Step 2 — Check every required ingredient

`hasAllIngredients()` (line 273) loops through every ingredient the recipe requires. For each one it:

1. Converts the required ingredient name to a canonical form using `canonical()`.
2. Converts the required quantity and unit into a `Measure` object using `measure()`.
3. Scans every pantry item, converts each matching item into a `Measure`, and adds up the available amount.
4. If the total available is less than required, it returns `false` immediately.

```java
if (available + 1e-9 < required.amount) {
    return false;
}
```

(line 294 — the small `1e-9` tolerance handles floating point rounding errors)

Only if every ingredient in the loop passes does the method return `true`.

### Step 3 — Name normalisation with canonical()

A naive string match would fail on "tomato" vs "tomatoes". The `canonical()` method (line 381) handles this by:

- Lowercasing the name and removing spaces
- Stripping a trailing `s` (tomatoes → tomato)
- Converting `ies` endings to `y` (berries → berry)
- Converting `oes` endings to `o` (tomatoes → tomato)

So "Tomato", "tomatoes", and "tomato" all map to the same canonical form and match each other.

### Step 4 — Unit conversion with measure()

A recipe might need `1 kg` of flour while the pantry has `1200 g`. Without conversion this would fail. The `measure()` method (line 399) converts everything to a base unit before comparing:

- `kg` is multiplied by 1000 and tagged as `mass`
- `g`, `gram`, `grams` are kept as-is and tagged as `mass`
- `l`, `litre`, `liter` etc. are multiplied by 1000 and tagged as `volume`
- `ml` is kept as-is and tagged as `volume`

Each value also carries a dimension tag. When checking available stock, the code only adds pantry quantities that share the same dimension as what the recipe requires:

```java
if (stock.dimension.equals(required.dimension)) {
    available += stock.amount;
}
```

(line 291)

This means a recipe that needs 500 ml of water will never be satisfied by 500 g of water — the dimensions do not match, so the stock is ignored and the recipe is correctly excluded.
