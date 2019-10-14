import com.vladsch.kotlin.jdbc.Transaction
import com.vladsch.kotlin.jdbc.session
import com.vladsch.kotlin.jdbc.sqlQuery
import edu.csus.ecs.pc2.api.exceptions.NotLoggedInException
import edu.csus.ecs.pc2.api.exceptions.LoginFailureException
import edu.csus.ecs.pc2.api.IContest
import edu.csus.ecs.pc2.api.IRunComparator
import edu.csus.ecs.pc2.api.ServerConnection
import edu.csus.ecs.pc2.core.IInternalController
import edu.csus.ecs.pc2.core.model.IInternalContest
import edu.csus.ecs.pc2.core.scoring.DefaultScoringAlgorithm
import edu.csus.ecs.pc2.core.scoring.NewScoringAlgorithm
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PublicServerConnection : ServerConnection() {
    val controller: IInternalController?
        get() = super.controller
    val internalContest: IInternalContest?
        get() = super.internalContest
}

const val PRESTIGE_UPDATE = "UPDATE TEAM SET PRESTIGE = ? WHERE ID = ?"

fun updatePrestige(contest: IContest, tx: Transaction) {
    val runs = contest.runs
    runs.sortWith(IRunComparator())

    val problemCount: MutableMap<String, Int> = hashMapOf()
    val teamPoints: MutableMap<Int, Int> = hashMapOf()
    val teamSolved: MutableMap<Int, MutableMap<String, Boolean>> = hashMapOf()
    for (run in runs) {
        if(run.isSolved) {
            val team = run.team
            val problem = run.problem

            if(run.team.accountNumber == 120) {
                continue
            }

            if(!teamSolved.computeIfAbsent(team.accountNumber) { hashMapOf() }.getOrDefault(problem.name, false)) {
                val curPoints = teamPoints.getOrDefault(team.accountNumber, 0)

                val curSolved = problemCount.getOrDefault(problem.name, 0)
                problemCount[problem.name] = curSolved + 1

                teamPoints[team.accountNumber] = curPoints +
                        when (curSolved) {
                            0 -> 5
                            1 -> 4
                            2 -> 3
                            3 -> 2
                            else -> 1
                        }

                teamSolved[team.accountNumber]!![problem.name] = true
            }
        }
    }

    for (team in 1..120) {
        tx.update(sqlQuery(PRESTIGE_UPDATE, teamPoints.getOrDefault(team, 0), team))
    }
}

const val SCORE_UPDATE = "UPDATE TEAM SET SCORE = ? WHERE ID = ?"

fun updateScore(connection: PublicServerConnection, tx: Transaction) {
    var properties: Properties? = connection.internalContest!!.contestInformation.scoringProperties
    if (properties == null) {
        properties = Properties()
    }

    val defProperties = DefaultScoringAlgorithm.getDefaultProperties()
    val keys = defProperties.keys.map { n -> n.toString() }.toTypedArray()

    for (i in keys.indices) {
        val key = keys[i]
        if (!properties.containsKey(key)) {
            properties[key] = defProperties[key]
        }
    }

    val scoringAlgorithm = NewScoringAlgorithm()
    val standings = scoringAlgorithm.getStandingsRecords(
        connection.internalContest,
        properties
    )

    for(i in 1..120) {
        tx.update(sqlQuery(SCORE_UPDATE, 0, i))
    }

    for (standing in standings) {
        val points = standing.penaltyPoints
        val team = standing.clientId.clientNumber

        tx.update(sqlQuery(SCORE_UPDATE, points, team))
    }
}

fun <T : ScheduledExecutorService> T.schedule(
    delay: Long,
    unit: TimeUnit = TimeUnit.SECONDS,
    action: () -> Unit
): ScheduledFuture<*> {
    return this.scheduleWithFixedDelay(
        action, 0,
        delay, unit
    )
}

fun main(args: Array<String>) {
    //[pc2Username, pc2Password, databaseJdbcUrl, databaseJdbcUsername, databaseJdbcPassword, runPrestige]
    try {
        val session = session(args[2], args[3], args[4])

        val scheduledExecutor = Executors.newScheduledThreadPool(1)

        val pc2Username = args[0]
        val pc2Password = args[1]

        val runPrestige = args[5].toBoolean()
        scheduledExecutor.schedule(15) {
            println("Querying")

            try {
                val serverConnection = PublicServerConnection()
                val contest = serverConnection.login(pc2Username, pc2Password)

                if(runPrestige) {
                    session.transaction {
                        updatePrestige(contest, it)
                    }
                }

                session.transaction {
                    updateScore(serverConnection, it)
                }

                serverConnection.logoff()
            } catch (ignored: Exception) {}
        }
    } catch (e: LoginFailureException) {
        println("Could not login")
        e.printStackTrace()
    } catch (e: NotLoggedInException) {
        println("Unable to execute API method")
        e.printStackTrace()
    }
}
