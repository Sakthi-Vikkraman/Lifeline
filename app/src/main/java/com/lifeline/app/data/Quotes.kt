package com.lifeline.app.data

import kotlin.random.Random

/** Original lines for rewards and the morning plan. Picks never repeat back to back. */
object Quotes {
    val productive = listOf(
        "One more hour in the bank. It compounds.",
        "You didn't feel like it. You did it anyway. That's the whole game.",
        "Reps build muscle. Hours build a life.",
        "Nobody's coming to save you. Good thing you showed up.",
        "Comfort just lost a round to you.",
        "Stack another one. Momentum is a weapon.",
        "The version of you that quits just lost to you.",
        "Sweat now. Flex later.",
        "You don't wait for motivation. You log the hour.",
        "That's how champions spend sixty minutes.",
        "Small win. Big pattern. Keep stacking.",
        "You're not lucky. You're consistent.",
        "Every hour you own is an hour nobody can take back.",
        "Pressure makes diamonds. Keep pressing.",
        "Don't stop when you're tired. Stop when you're done.",
        "Built, not born.",
        "Another brick in the wall. It's getting tall.",
        "You just outworked yesterday's you.",
        "Talk is cheap. This hour wasn't.",
        "Lock in. The next hour is already watching.",
        "Your future self just said thanks.",
        "No shortcuts. Just hours. Keep going.",
        "Grind quietly. Let the results be loud.",
        "Do it again. That's the whole secret.",
        "Champions are made in hours nobody sees.",
        "Heavy hour. Lifted.",
        "Proof logged. Excuses deleted.",
        "Keep the bar moving. Don't rack it yet.",
        "That's a rep for the person you're becoming.",
        "Hungry hours, full life.",
    )

    val goal = listOf(
        "Goal crushed. Everything from here is extra plates on the bar.",
        "Daily target hit. Most people never even set one.",
        "That's the full set. Now see how far past it you can go.",
        "Goal done. You kept your word to yourself.",
        "Target smashed. Raise it tomorrow.",
    )

    val entertainment = listOf(
        "Logged. Fun's fine — just don't let it run your day.",
        "Rest is part of training. Make sure it's rest, not escape.",
        "Honesty logged. Now earn the next hour.",
        "That hour's spent. The next one is still yours.",
        "Break taken. When the alarm hits, you're back.",
        "Owning it is step one. Step two is the next hour.",
        "Logged, not judged. Just don't make it a habit.",
        "Recharge, then attack.",
    )

    val morning = listOf(
        "Today is a blank page. Write something worth reading.",
        "Win the morning. Own the day.",
        "The plan is set. Now be the person who follows it.",
        "Nobody remembers the days you played it safe.",
        "Today's pain is tomorrow's power.",
        "Be so locked in they think you vanished.",
        "Plan the work. Work the plan. No excuses.",
        "Hungry beats talented when talented sleeps in.",
        "Every hour today is a rep. Count them all.",
        "You wrote the plan. Now go prove you meant it.",
        "The comfort zone is closed today.",
        "Be the problem your excuses can't solve.",
        "A year from now you'll be glad you started today.",
        "Show up. Shut up. Stack hours.",
        "Your only competition is who you were yesterday.",
        "Average is crowded. Go where it's empty.",
        "No days off from becoming who you said you'd be.",
        "Feelings are loud. Your plan is louder.",
        "The day doesn't care how you feel. Show up anyway.",
        "Make today the day the old you can't recognise.",
    )

    fun pick(prefs: Prefs, name: String, list: List<String>): String {
        val last = prefs.lastQuote(name)
        var i = Random.nextInt(list.size)
        if (list.size > 1 && i == last) i = (i + 1) % list.size
        prefs.setLastQuote(name, i)
        return list[i]
    }
}
