package org.tkit.onecx.parameters.domain.daos;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;

import org.tkit.onecx.parameters.domain.criteria.ApplicationParameterSearchCriteria;
import org.tkit.onecx.parameters.domain.criteria.KeysSearchCriteria;
import org.tkit.onecx.parameters.domain.models.*;
import org.tkit.quarkus.jpa.daos.AbstractDAO;
import org.tkit.quarkus.jpa.daos.Page;
import org.tkit.quarkus.jpa.daos.PageResult;
import org.tkit.quarkus.jpa.exceptions.DAOException;

@ApplicationScoped
@Transactional(value = Transactional.TxType.NOT_SUPPORTED, rollbackOn = DAOException.class)
public class ApplicationParameterDAO extends AbstractDAO<ApplicationParameter> {

    // keys in format <app><separator><key>
    public Map<String, ApplicationParameter> findApplicationParametersByKeys(Set<String> keys, String separator) {
        try {
            CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
            CriteriaQuery<ApplicationParameter> cq = cb.createQuery(ApplicationParameter.class);
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);

            // 'app__key' in ( ... )
            cq.where(cb.concat(
                    cb.concat(root.get(ApplicationParameter_.APPLICATION_ID), separator), root.get(ApplicationParameter_.KEY))
                    .in(keys));

            List<ApplicationParameter> params = getEntityManager().createQuery(cq).getResultList();
            return params.stream().collect(Collectors.toMap(x -> x.getApplicationId() + separator + x.getKey(), x -> x));
        } catch (Exception e) {
            throw new DAOException(ErrorKeys.FIND_PARAMETER_BY_APPLICATION_ID_AND_PARAMETER_KEY_FAILED, e);
        }
    }

    public List<ApplicationParameter> findByApplicationIdAndParameterKeys(String applicationId, List<String> parametersKeys) {

        try {
            CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
            CriteriaQuery<ApplicationParameter> cq = cb.createQuery(ApplicationParameter.class);
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);
            cq.where(cb.and(
                    cb.equal(root.get(ApplicationParameter_.APPLICATION_ID), applicationId),
                    root.get(ApplicationParameter_.KEY).in(parametersKeys)));
            return getEntityManager().createQuery(cq).getResultList();
        } catch (Exception e) {
            throw new DAOException(ErrorKeys.FIND_PARAMETERS_BY_APPLICATION_AND_PARAMETER_KEYS_FAILED, e);
        }
    }

    public Map<String, String> findAllByProductNameAndApplicationId(String productName, String applicationId) {
        try {
            CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
            CriteriaQuery<ApplicationParameter> cq = cb.createQuery(ApplicationParameter.class);
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);
            cq.where(cb.and(
                    cb.equal(root.get(ApplicationParameter_.PRODUCT_NAME), productName),
                    cb.equal(root.get(ApplicationParameter_.APPLICATION_ID), applicationId),
                    cb.or(
                            cb.isNotNull(root.get(ApplicationParameter_.SET_VALUE)),
                            cb.isNotNull(root.get(ApplicationParameter_.IMPORT_VALUE)))));

            return getEntityManager()
                    .createQuery(cq)
                    .getResultStream()
                    .collect(Collectors.toMap(ApplicationParameter::getKey,
                            p -> p.getSetValue() != null ? p.getSetValue() : p.getImportValue()));

        } catch (Exception e) {
            throw new DAOException(ErrorKeys.FIND_ALL_PARAMETERS_BY_APPLICATION_ID_FAILED, e);
        }
    }

    public PageResult<ApplicationParameter> searchByCriteria(ApplicationParameterSearchCriteria criteria) {
        try {
            CriteriaQuery<ApplicationParameter> cq = criteriaQuery();
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);
            List<Predicate> predicates = new ArrayList<>();
            CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
            if (criteria.getProductName() != null && !criteria.getProductName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get(ApplicationParameter_.PRODUCT_NAME)),
                        stringPattern(criteria.getProductName())));
            }
            if (criteria.getApplicationId() != null && !criteria.getApplicationId().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get(ApplicationParameter_.APPLICATION_ID)),
                        stringPattern(criteria.getApplicationId())));
            }
            if (criteria.getKey() != null && !criteria.getKey().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get(ApplicationParameter_.KEY)), stringPattern(criteria.getKey())));
            }
            if (criteria.getName() != null && !criteria.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get(ApplicationParameter_.NAME)), stringPattern(criteria.getName())));
            }
            if (!predicates.isEmpty()) {
                cq.where(cb.and(predicates.toArray(new Predicate[0])));
            }
            return createPageQuery(cq, Page.of(criteria.getPageNumber(), criteria.getPageSize())).getPageResult();
        } catch (Exception exception) {
            throw new DAOException(ErrorKeys.FIND_ALL_PARAMETERS_FAILED, exception);
        }
    }

    public PageResult<String> searchAllKeys(KeysSearchCriteria criteria) {
        try {
            var cb = getEntityManager().getCriteriaBuilder();
            CriteriaQuery<String> cq = cb.createQuery(String.class);
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);
            cq.select(root.get(ApplicationParameter_.KEY)).distinct(true);

            if (criteria.getProductName() != null && !criteria.getProductName().isEmpty()) {
                cq.where(cb.equal(root.get(ApplicationParameter_.PRODUCT_NAME), criteria.getProductName()));
            }

            if (criteria.getApplicationId() != null && !criteria.getApplicationId().isEmpty()) {
                cq.where(cb.equal(root.get(ApplicationParameter_.APPLICATION_ID), criteria.getApplicationId()));
            }

            var results = getEntityManager().createQuery(cq).getResultList();
            return new PageResult<>(results.size(), results.stream(), Page.of(0, 1));
        } catch (Exception exception) {
            throw new DAOException(ErrorKeys.FIND_ALL_KEYS_FAILED, exception);
        }
    }

    public List<ApplicationTuple> searchAllProductNamesAndApplicationIds() {
        try {
            var cb = getEntityManager().getCriteriaBuilder();
            CriteriaQuery<ApplicationTuple> cq = cb.createQuery(ApplicationTuple.class);
            Root<ApplicationParameter> root = cq.from(ApplicationParameter.class);
            cq.select(
                    cb.construct(ApplicationTuple.class, root.get(ApplicationParameter_.PRODUCT_NAME),
                            root.get(ApplicationParameter_.APPLICATION_ID)))
                    .distinct(true);
            return getEntityManager().createQuery(cq).getResultList();
        } catch (Exception exception) {
            throw new DAOException(ErrorKeys.FIND_ALL_APPLICATIONS_FAILED, exception);
        }
    }

    private static String stringPattern(String value) {
        return (value.toLowerCase() + "%");
    }

    public enum ErrorKeys {

        FIND_ALL_PARAMETERS_BY_APPLICATION_ID_FAILED,
        FIND_PARAMETER_BY_APPLICATION_ID_AND_PARAMETER_KEY_FAILED,
        FIND_PARAMETERS_BY_APPLICATION_AND_PARAMETER_KEYS_FAILED,
        FIND_PARAMETERS_BY_APPLICATION_AND_PARAMETER_KEY_TYPE_FAILED,
        FIND_ALL_APPLICATIONS_FAILED,

        FIND_ALL_KEYS_FAILED,
        FIND_ALL_PARAMETERS_FAILED;
    }
}
