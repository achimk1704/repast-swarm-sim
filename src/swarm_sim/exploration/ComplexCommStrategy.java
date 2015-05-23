package swarm_sim.exploration;

import java.util.ArrayList;
import java.util.List;

import org.jgap.IChromosome;

import repast.simphony.context.Context;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.SpatialMath;
import repast.simphony.space.continuous.NdPoint;
import swarm_sim.AbstractAgent;
import swarm_sim.Agent;
import swarm_sim.Agent.AgentState;
import swarm_sim.Strategy;
import swarm_sim.communication.CommunicationType;
import swarm_sim.communication.INetworkAgent;
import swarm_sim.communication.Message;
import swarm_sim.communication.Message.MessageType;
import swarm_sim.learning.GA;
import swarm_sim.perception.AngleSegment;
import swarm_sim.perception.Scan;
import swarm_sim.perception.Scan.AttractionType;
import swarm_sim.perception.Scan.GrowingDirection;
import swarm_sim.perception.ScanMoveDecision;

public class ComplexCommStrategy extends ExplorationStrategy {

    private static final int INFINITY = 99999999;

    private double prevDirection = RandomHelper.nextDoubleFromTo(-Math.PI,
	    Math.PI);
    private int consecutiveMoveCount = INFINITY;

    private Scan scanAgentRepell = new Scan(AttractionType.Repelling,
	    GrowingDirection.Inwards, 2, false, 0, 0.7 * config.commScope, 1,
	    1000);
    private Scan scanAgentAppeal = new Scan(AttractionType.Attracting,
	    GrowingDirection.Outwards, 0.2, false, 0.8 * config.commScope,
	    config.commScope, 1, 1000);
    private Scan scanAgentMimic = new Scan(AttractionType.Attracting,
	    GrowingDirection.Inwards, 1, true, 0, config.commScope, 1, 1000);
    private Scan scanPrevDirection = new Scan(AttractionType.Attracting,
	    GrowingDirection.Inwards, 1, true, 0, 1000, 1, 1000);

    private ScanMoveDecision smd;

    public ComplexCommStrategy(IChromosome chrom,
	    Context<AbstractAgent> context, Agent controllingAgent) {
	super(chrom, context, controllingAgent);

	smd = new ScanMoveDecision(config.segmentCount, config.k,
		config.distanceFactor, config.initProb);

	if (config.useGA) {
	    GA ga = GA.getInstance();

	    scanAgentRepell.setMergeWeight((double) chrom.getGene(
		    ga.RepellIndex).getAllele());
	    scanAgentAppeal.setMergeWeight((double) chrom.getGene(
		    ga.AppealIndex).getAllele());
	    scanAgentMimic.setMergeWeight((double) chrom.getGene(ga.MimicIndex)
		    .getAllele());
	    scanPrevDirection.setMergeWeight((double) chrom.getGene(
		    ga.PrevDirectionIndex).getAllele());

	    double repellAppealBorder = config.commScope
		    * (double) chrom.getGene(ga.AppealRepellBorderIndex)
			    .getAllele();
	    scanAgentRepell.setOuterBorderRadius(repellAppealBorder);
	    scanAgentAppeal.setInnerBorderRadius(repellAppealBorder);
	} else {

	    // double winningGenes2[] = new double[] { 0.43, 0.06, 0.94, 0.03,
	    // 0.71 }; → 1030 avg after 50
	    // double winningGenes[] = new double[] { 0.94, 0.36, 0.99, 0.00,
	    // 0.76 }; // →
	    // 981
	    // avg
	    // after
	    // 50
	    // double winningGenes[] = new double[] { 0.9144661462973355,
	    // 0.009199286207776658, 0.7413437169753542,
	    // 0.02745376616225159, 0.9807642605312746 };

	    // test
	    double winningGenes[] = new double[] { 1, 1, .8, 1, 0.1 };

	    GA ga = GA.getInstance();

	    ga.RepellIndex = 0;
	    ga.AppealIndex = 1;
	    ga.AppealRepellBorderIndex = 2;
	    ga.MimicIndex = 3;
	    ga.PrevDirectionIndex = 4;

	    scanAgentRepell.setMergeWeight(winningGenes[0]);
	    scanAgentAppeal.setMergeWeight(winningGenes[1]);
	    scanAgentMimic.setMergeWeight(winningGenes[3]);
	    scanPrevDirection.setMergeWeight(winningGenes[4]);

	    double repellAppealBorder = config.commScope * winningGenes[2];
	    scanAgentRepell.setOuterBorderRadius(repellAppealBorder);
	    scanAgentAppeal.setInnerBorderRadius(repellAppealBorder);
	}
    }

    @Override
    protected List<MessageTypeRegisterPair> getMessageTypesToRegister(
	    CommunicationType[] allowedCommTypes) {
	List<MessageTypeRegisterPair> ret = new ArrayList<Strategy.MessageTypeRegisterPair>();
	for (CommunicationType commType : allowedCommTypes) {
	    switch (commType) {
	    case Location:
		AgentState states[] = new AgentState[] { AgentState.wander };
		ret.add(new MessageTypeRegisterPair(MessageType.Location,
			states));
		break;
	    case TargetOrDirection:
		AgentState states2[] = new AgentState[] { AgentState.wander };
		ret.add(new MessageTypeRegisterPair(MessageType.Direction,
			states2));
	    default:
		break;
	    }
	}
	return ret;
    }

    @Override
    protected AgentState processMessage(AgentState prevState,
	    AgentState currentState, Message msg, boolean isLast) {
	if (isLast)
	    return currentState;

	if (msg.getType() == MessageType.Location) {
	    NdPoint currentLoc = space.getLocation(controllingAgent);
	    NdPoint agentLoc = space.getLocation(msg.getSender());
	    double distance = space.getDistance(currentLoc, agentLoc);
	    double angle = SpatialMath.calcAngleFor2DMovement(space,
		    currentLoc, agentLoc);
	    scanAgentAppeal.addInput(angle, distance);
	    consecutiveMoveCount = INFINITY;

	    /* Do not add to repell, if the border of the search area is closer */
	    double delta[] = space.getDisplacement(agentLoc, currentLoc);
	    double p[] = new double[] { currentLoc.getX() + delta[0],
		    currentLoc.getY() + delta[1] };

	    if (p[0] <= config.spaceWidth && p[0] >= 0 && p[1] >= 0
		    && p[1] <= config.spaceHeight) {
		scanAgentRepell.addInput(angle, distance);
		consecutiveMoveCount = INFINITY;
	    }
	} else if (msg.getType() == MessageType.Direction) {
	    NdPoint currentLoc = space.getLocation(controllingAgent);
	    NdPoint agentLoc = space.getLocation(msg.getSender());
	    double distance = space.getDistance(currentLoc, agentLoc);
	    scanAgentMimic.addInput((double) msg.getData(), distance);
	    consecutiveMoveCount = INFINITY;
	}

	return currentState;
    }

    @Override
    protected void sendMessage(AgentState prevState, AgentState currentState,
	    INetworkAgent agentInRange) {
	if (currentState == AgentState.wander) {
	    agentInRange.pushMessage(new Message(MessageType.Location,
		    controllingAgent, space.getLocation(controllingAgent)));
	    agentInRange.pushMessage(new Message(MessageType.Direction,
		    controllingAgent, prevDirection));
	}
    }

    @Override
    protected double makeDirectionDecision(AgentState prevState,
	    AgentState currentState, List<AngleSegment> collisionFreeSegments) {

	if (consecutiveMoveCount < config.consecutiveMoves) {
	    /* use same direction distribution, if possible */
	    if (collisionFreeSegments.size() == 1
		    && collisionFreeSegments.get(0).start == -Math.PI) {
		/* free to go in any direction */
		consecutiveMoveCount++;
		if (smd.hasInputs()) /*
				      * Only use previous prob distribution if
				      * it is not uniformly
				      */
		    prevDirection = smd.getMovementAngle();

		return prevDirection;
	    }
	}

	/* not able to go into same direction, or consecutiveMoveCount too high */
	consecutiveMoveCount = 0;
	smd.clear();

	scanPrevDirection.addInput(prevDirection);

	smd.setValidSegments(collisionFreeSegments);
	smd.calcProbDist(scanAgentAppeal, scanAgentRepell, scanAgentMimic,
		scanPrevDirection);
	smd.normalize();
	// smd.printProbabilities(null);

	prevDirection = smd.getMovementAngle();

	return prevDirection;
    }

    @Override
    protected void clear() {
	scanAgentAppeal.clear();
	scanAgentRepell.clear();
	scanAgentMimic.clear();
	scanPrevDirection.clear();
	// smd is cleared in makemovedecision
    }

    @Override
    protected void reset() {
	prevDirection = RandomHelper.nextDoubleFromTo(-Math.PI, Math.PI);
	smd.clear();
	consecutiveMoveCount = INFINITY;
    }

}
