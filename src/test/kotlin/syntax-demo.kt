@file:Suppress("ktlint:standard:filename", "ktlint:standard:no-consecutive-comments", "ktlint:standard:function-naming")

/**
 * kotlin syntax - brief
 */

fun main() {
    println("Running syntax-demo - uncomment things at your leisure")
    /**
     * Nulls part of type system (addressing notorious Java NullPointerException)
     */
    var notnull = "I cannot be null"

    // Nullable type
    var name: String? = null

    // Safe null check - Safe call operation ?.
    val l1 = name?.length

    // Elvis operator ?: (if null then)
    val l2 = name?.length ?: 0

    // Force unwrap (Use with caution)
    // val l3 = name!!.length

    /**
     * Extensions - Extend existing classes with new functionality without inheriting from them. This promotes cleaner and more readable code.
     */
    // Extension function
    fun String.addHello(): String = "Hello, $this"

    // Usage
    val greeting = "Platypus".addHello()

    // println(greeting)

    /**
     * Data classes - Create classes for data storage with minimal boilerplate code. Automatically generates toString(), equals(), hashCode(), and copy() methods.
     * Compare with POJO classes in Java
     */

    // data
    class UserPOJO(
        val name: String,
        val age: Int,
    )

    val alice1 = UserPOJO("Alice", 1)
    val alice2 = UserPOJO("Alice", 1)

    // println("alice1 $alice1, equals to alice2 ${alice1 == alice2}")

    /**
     * Smart casts - Kotlin smart casts automatically cast types after a type check, reducing boilerplate code.
     **/
    // Type check and automatic cast
    fun process(obj: Any) {
        if (obj is String) {
            println(obj.length) // No explicit casting needed
        }
    }

    /**
     * Lambda expressions - Simplify coding with anonymous functions. Useful for passing functions as arguments or defining concise functionality.
     */
    // Lambda expression
    val sum = { x: Int, y: Int -> x + y }

    // Usage
    val result = sum(5, 10) // Result: 15

    /**
     * Scope functions - Scope functions allow concise ways to operate on objects within a specific context, avoiding repetitive object references.
     */
    // Scope functions
    val user =
        UserPOJO("John", 25)
            .apply {
                // println("User: $name, Age: $age")
            }.also {
                // Additional operations if needed
                // println("Additional operations on user")
            }

    /**
     * let: Executes the block of code if the object is not null, allowing safe access to the object within the block.
     * It returns the result of the lambda expression.
     *
     * run: Invokes the block of code on the object itself, returning the result of the lambda expression. It is useful
     * for performing operations within the context of the object.
     */
    user?.let {
        // Safe access to 'data'
        // println(it.name)
    }

    user?.run {
        // println("$name")
    }

    /**
     * chain functions on iterables
     */
    // println((1..10).map { "Number $it" }.filter { it != "Number 1" })
}
