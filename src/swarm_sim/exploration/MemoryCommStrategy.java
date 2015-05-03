package swarm_sim.exploration;

import java.util.ArrayList;
import java.util.List;

import org.jgap.Chromosome;

import repast.simphony.context.Context;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.NdPoint;
import swarm_sim.Agent;
import swarm_sim.Agent.AgentState;
import swarm_sim.IAgent;
import swarm_sim.IAgent.AgentType;
import swarm_sim.SectorMap;
import swarm_sim.Strategy;
import swarm_sim.communication.CommunicationType;
import swarm_sim.communication.INetworkAgent;
import swarm_sim.communication.Message;
import swarm_sim.communication.Message.MessageType;
import swarm_sim.perception.AngleSegment;
import swarm_sim.perception.CircleScan;

public class MemoryCommStrategy extends ExplorationStrategy {

    int segmentCount = 8;

    SectorMap map = new SectorMap(space.getDimensions(), 60, 60, 1);
    CircleScan memoryFollow = new CircleScan(segmentCount, 1, 1, 1000, 1, 1, 2);

    public MemoryCommStrategy(Chromosome chrom, Context<IAgent> context,
	    Agent controllingAgent) {
	super(chrom, context, controllingAgent);
    }

    @Override
    protected AgentState processMessage(AgentState prevState,
	    AgentState currentState, Message msg, boolean isLast) {
	if (isLast)
	    return currentState;

	if (msg.getType() == MessageType.SectorMap)
	    map.merge((SectorMap) msg.getData());

	return currentState;
    }

    @Override
    protected void sendMessage(AgentState prevState, AgentState currentState,
	    INetworkAgent agentInRange) {
	if (currentState == AgentState.wander) {
	    agentInRange.pushMessage(new Message(MessageType.SectorMap,
		    controllingAgent, map));
	}
    }

    @Override
    protected AgentState processPerceivedAgent(AgentState prevState,
	    AgentState currentState, IAgent agent, boolean isLast) {
	if (agent.getAgentType() == AgentType.Resource)
	    return AgentState.acquire;

	return AgentState.wander;
    }

    @Override
    protected double makeDirectionDecision(AgentState prevState,
	    AgentState currentState, List<AngleSegment> collisionFreeSegments) {
	NdPoint currentLocation = space.getLocation(controllingAgent);

	map.setPosition(currentLocation);
	List<Integer[]> closeUnfilledSectors = map.getCloseUnfilledSectors(5);
	for (Integer[] d : closeUnfilledSectors) {
	    double angle = SpatialMath.angleFromDisplacement(d[0], d[1]);
	    double distance = Math.sqrt(d[0] * d[0] + d[1] * d[1]);
	    memoryFollow.add(angle, distance);
	}

	CircleScan res = CircleScan.merge(segmentCount, 0.12,
		collisionFreeSegments, memoryFollow);
	return res.getMovementAngle();
    }

    @Override
    protected void clear() {
	memoryFollow.clear();
    }

    @Override
    protected void reset() {
	// set current sector unfilled, so agent will return here some time
	map.setCurrentSectorUnfilled();
    }

    @Override
    protected List<MessageTypeRegisterPair> getMessageTypesToRegister(
	    CommunicationType[] allowedCommTypes) {
	List<MessageTypeRegisterPair> ret = new ArrayList<Strategy.MessageTypeRegisterPair>();
	for (CommunicationType commType : allowedCommTypes) {
	    switch (commType) {
	    case MapOrTargets:
		AgentState states[] = new AgentState[] { AgentState.wander };
		ret.add(new MessageTypeRegisterPair(MessageType.SectorMap,
			states));
		break;
	    default:
		break;
	    }
	}
	return ret;
    }

}