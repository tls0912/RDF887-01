package com.czkuo.rdf88701.infra.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czkuo.rdf88701.application.dto.query.UsersQuery;
import com.czkuo.rdf88701.common.dto.PageResult;
import com.czkuo.rdf88701.common.util.LambdaQueryHelper;
import com.czkuo.rdf88701.domain.repository.UsersRepository;
import com.czkuo.rdf88701.infra.entity.Users;
import com.czkuo.rdf88701.infra.mapper.UsersMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UsersRepositoryImpl implements UsersRepository {

    private final UsersMapper usersMapper;

    public UsersRepositoryImpl(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }

    @Override
    public Optional<Users> findById(Long id) {
        return Optional.ofNullable(usersMapper.selectById(id));
    }

    @Override
    public Optional<Users> findByUsername(String username) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        return Optional.ofNullable(usersMapper.selectOne(wrapper));
    }

    @Override
    public boolean existsByUsername(String username) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username).last("LIMIT 1");
        return usersMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<Users> findByCondition(UsersQuery query) {
        LambdaQueryHelper<Users> helper = buildQueryWrapper(query);
        return usersMapper.selectList(helper.getWrapper());
    }

    @Override
    public PageResult<Users> findPageByCondition(UsersQuery query) {
        LambdaQueryHelper<Users> helper = buildQueryWrapper(query);
        Page<Users> page = new Page<>(query.getSafePageNum(), query.getSafePageSize());
        IPage<Users> result = usersMapper.selectPage(page, helper.getWrapper());
        return new PageResult<>(query.getSafePageNum(), query.getSafePageSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public boolean save(Users user) {
        return usersMapper.insert(user) > 0;
    }

    @Override
    public boolean update(Users user) {
        return usersMapper.updateById(user) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return usersMapper.deleteById(id) > 0;
    }

    @Override
    public List<Users> findAll() {
        return usersMapper.selectList(null);
    }

    private LambdaQueryHelper<Users> buildQueryWrapper(UsersQuery query) {
        return LambdaQueryHelper.<Users>of()
                .eqIfPresent(Users::getId, query::getId)
                .eqIfPresent(Users::getUsername, query::getUsername)
                .eqIfPresent(Users::getRoleId, query::getRoleId)
                .geIfPresent(Users::getCreatedAt, query::getCreatedAfter)
                .leIfPresent(Users::getCreatedAt, query::getCreatedBefore);
    }
}
