package swarm_sim;

import com.vividsolutions.jts.geom.Geometry;

public class Pheromone implements Agent {

	
	public Pheromone() {
	}
	
	@Override
	public String getName() {
		return "Pheromone agent";
	}
	
	@Override
	public AgentType getAgentType() {
		// TODO Auto-generated method stub
		return AgentType.Pheromone;
	}
}