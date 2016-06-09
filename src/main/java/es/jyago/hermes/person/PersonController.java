package es.jyago.hermes.person;

import es.jyago.hermes.util.MessageBundle;
import java.io.Serializable;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

@Named("personController")
@ApplicationScoped
public class PersonController implements Serializable {

    private static final Logger LOG = Logger.getLogger(PersonController.class.getName());

    private Person selected;

    @Inject
    @MessageBundle
    private ResourceBundle bundle;

    public PersonController() {
        LOG.log(Level.INFO, "PersonController() - Inicialización del controlador de personas");

        selected = null;
    }

    public Person getSelected() {
        return selected;
    }

    public void setSelected(Person selected) {
        this.selected = selected;
    }
}
