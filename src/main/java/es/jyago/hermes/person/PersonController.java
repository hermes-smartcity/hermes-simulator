package es.jyago.hermes.person;

import es.jyago.hermes.util.JsfUtil;
import es.jyago.hermes.util.JsfUtil.PersistAction;
import es.jyago.hermes.util.MessageBundle;
import es.jyago.hermes.util.Util;
import java.io.IOException;
import java.io.Serializable;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJBException;
import javax.enterprise.context.ApplicationScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.primefaces.context.RequestContext;

@Named("personController")
@ApplicationScoped
public class PersonController implements Serializable {

    private static final Logger LOG = Logger.getLogger(PersonController.class.getName());

    @Inject
    private PersonFacade personFacade;
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

    private PersonFacade getFacade() {
        return personFacade;
    }

    public Person prepareCreate() {
        selected = new Person();

        return selected;
    }

    public void create() {
        persist(PersistAction.CREATE, bundle.getString("PersonCreated"));
    }

    public void update() {
        update(true);
    }

    public void update(boolean showMessage) {
        String message = null;
        if (showMessage) {
            message = bundle.getString("PersonUpdated");
        }
        persist(PersistAction.UPDATE, message);
    }

    public void destroy() {
        persist(PersistAction.DELETE, bundle.getString("PersonDeleted"));
        if (!JsfUtil.isValidationFailed()) {
            selected = null;
        }
    }

    private void persist(PersistAction persistAction, String successMessage) {
        if (selected != null) {
            try {
                if (persistAction != PersistAction.DELETE) {
                    // Cuando vayamos a guardar los datos de la persona, calculamos el SHA del e-mail.
                    selected.setSha(new String(Hex.encodeHex(DigestUtils.sha256(selected.getEmail()))));
                    getFacade().edit(selected);
                } else {
                    getFacade().remove(selected);
                }
                if (successMessage != null) {
                    JsfUtil.addSuccessMessage(successMessage);
                }
            } catch (EJBException ex) {
                // Activamos la bandera para indicar que ha habido un error.
                FacesContext.getCurrentInstance().validationFailed();
                JsfUtil.showDetailedExceptionCauseMessage(ex);
            } catch (Exception ex) {
                FacesContext.getCurrentInstance().validationFailed();
                LOG.log(Level.SEVERE, "persist() - Error al registrar los cambios", ex);
                // TODO: Usar esta forma para mostrar los mensajes!
                // TODO: Incluso poner el 'bundle' en JsfUtil y enviar sólo la key.
                JsfUtil.addErrorMessage(ex, bundle.getString("PersistenceErrorOccured"));
            }
        }
    }

    public Person getPerson(java.lang.Integer id) {
        return getFacade().find(id);
    }

    public void checkEmail() {
        if (!Util.isValidEmail(selected.getEmail())) {
            LOG.log(Level.SEVERE, "checkEmail() - El email de la persona no es válido: {0}", selected.getEmail());
            FacesContext.getCurrentInstance().validationFailed();
            JsfUtil.addErrorMessage(bundle.getString("CheckEmail"));
            RequestContext.getCurrentInstance().execute("PF('PersonEditDialog').show()");
        } else {
            try {
                ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
                ec.redirect(ec.getRequestContextPath() + "/faces/secured/alert/List.xhtml");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "checkEmail() - Error al redirigir al listado de alertas", ex);
            }
        }
    }
}
