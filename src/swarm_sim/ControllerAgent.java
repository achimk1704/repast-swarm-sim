package swarm_sim;

import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.graph.Network;
import swarm_sim.Scenario.AgentDistancePairs;

public class ControllerAgent implements IAgent {
    private Context<IAgent> context;
    private Network<IAgent> commNet;

    private ContinuousSpace<IAgent> space;
    protected AdvancedGridValueLayer exploredArea;

    private Scenario scenario;
    IIsSimFinishedFunction simFinishedFunc;

    public ControllerAgent(Context<IAgent> context,
	    IIsSimFinishedFunction simFinishedFunc) {
	this.context = context;
	this.commNet = context.getProjection(Network.class, "network_comm");
	this.space = (ContinuousSpace<IAgent>) context.getProjection(
		ContinuousSpace.class, "space_continuous");
	this.scenario = Scenario.getInstance();
	this.exploredArea = (AdvancedGridValueLayer) context
		.getValueLayer("layer_explored");
	this.simFinishedFunc = simFinishedFunc;
    }

    public void step() {
	// for (int i = 0; i < scenario.movebins.length; i++) {
	// System.out.print(scenario.movebins[i] + ", ");
	// }
	// System.out.println();

	/* Check if Simulation is done */
	if (simFinishedFunc.isSimFinished(context, exploredArea))
	    RunEnvironment.getInstance().endRun();

	if (!scenario.isInitiated())
	    scenario.init();

	int tick = (int) RunEnvironment.getInstance().getCurrentSchedule()
		.getTickCount();

	commNet.removeEdges();
	for (AgentDistancePairs agentPair : scenario.agentDistancePairs) {
	    boolean toBeChecked = (tick - agentPair.lastTimeChecked) * 2
		    * scenario.maxMoveDistance >= Math.abs(agentPair.distance
		    - scenario.commScope);

	    if (toBeChecked) {
		agentPair.distance = space.getDistance(
			space.getLocation(agentPair.source),
			space.getLocation(agentPair.target));
		agentPair.lastTimeChecked = tick;
	    }

	    if (agentPair.distance <= scenario.commScope
		    && !exploredArea.isObstacleOnLine(
			    space.getLocation(agentPair.source),
			    space.getLocation(agentPair.target)))
		commNet.addEdge(agentPair.source, agentPair.target);
	}
    }

    @Override
    public AgentType getAgentType() {
	// TODO Auto-generated method stub
	return IAgent.AgentType.ControllerAgent;
    }
}
