package es.jyago.hermes.location;

import es.jyago.hermes.AbstractFacade;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;


@Stateless
public class LocationLogFacade extends AbstractFacade<LocationLog> {

    @PersistenceContext(unitName = "HermesWeb_PU")
    private EntityManager em;

    @Override
    public EntityManager getEntityManager() {
        return em;
    }

    public LocationLogFacade() {
        super(LocationLog.class);
    }
}
